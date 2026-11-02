package com.dongqh.fulfillment.service;

import com.dongqh.fulfillment.messaging.FulfillmentTaskReadyEvent;
import com.dongqh.fulfillment.persistence.FulfillmentOutboxCandidate;
import com.dongqh.fulfillment.persistence.FulfillmentOutboxRepository;
import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import com.dongqh.fulfillment.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillment.enums.shipping.ShippingStatus;
import com.dongqh.fulfillment.service.port.FulfillmentTaskReadyPublisher;
import com.dongqh.fulfillment.service.port.FulfillmentStatusChangedPublisher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentOutboxRelayServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T04:00:00Z"), ZoneOffset.UTC);

    @Test
    void claimsCandidatesPublishesAndMarksOnlyConfirmedRowsSent() {
        FakeRepository repository = new FakeRepository(List.of(
                candidate(1, 0), candidate(2, 1), candidate(3, 0)));
        repository.claimableIds.addAll(List.of(1L, 3L));
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingStatusPublisher statusPublisher = new RecordingStatusPublisher();
        FulfillmentOutboxRelayService service = service(repository, publisher, statusPublisher);

        int relayed = service.relayBatch();

        assertThat(relayed).isEqualTo(2);
        assertThat(publisher.events).extracting(FulfillmentTaskReadyEvent::fulfillmentNo)
                .containsExactly("TASK-1", "TASK-3");
        assertThat(repository.sentIds).containsExactly(1L, 3L);
        assertThat(repository.claims).hasSize(3);
        assertThat(repository.claims.get(0).claimUntil())
                .isEqualTo(LocalDateTime.of(2026, 9, 27, 4, 0).plusSeconds(30));
        assertThat(statusPublisher.events).isEmpty();
    }

    @Test
    void statusChangedOutboxPayloadUsesItsOwnPublisherAndIsNotDecodedAsTaskReady() {
        FakeRepository repository = new FakeRepository(List.of(
                candidate(6, FulfillmentStatusChangedPublisher.EVENT_TYPE, 0)));
        repository.claimableIds.add(6L);
        RecordingPublisher taskPublisher = new RecordingPublisher();
        RecordingStatusPublisher statusPublisher = new RecordingStatusPublisher();

        int relayed = service(repository, taskPublisher, statusPublisher).relayBatch();

        assertThat(relayed).isOne();
        assertThat(taskPublisher.events).isEmpty();
        assertThat(statusPublisher.events).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo("EVENT-6");
            assertThat(event.sourceType()).isEqualTo(ShippingSourceType.LOTTERY_BENEFIT.name());
            assertThat(event.sourceId()).isEqualTo("BENEFIT-6");
            assertThat(event.status()).isEqualTo(ShippingStatus.DELIVERED.name());
        });
        assertThat(repository.sentIds).containsExactly(6L);
    }

    @Test
    void eachOutboxRowGetsALeaseStartingAtItsActualClaimTime() {
        MutableClock clock = new MutableClock(CLOCK.instant());
        FakeRepository repository = new FakeRepository(List.of(candidate(1, 0), candidate(2, 0)));
        repository.claimableIds.addAll(List.of(1L, 2L));
        RecordingPublisher publisher = new RecordingPublisher();
        publisher.beforeNextPublish = () -> clock.advance(Duration.ofMinutes(2));

        service(repository, publisher, new RecordingStatusPublisher(), clock).relayBatch();

        assertThat(repository.claims).hasSize(2);
        assertThat(repository.claims.get(0).now()).isEqualTo(LocalDateTime.of(2026, 9, 27, 4, 0));
        assertThat(repository.claims.get(1).now()).isEqualTo(LocalDateTime.of(2026, 9, 27, 4, 2));
        assertThat(repository.claims.get(1).claimUntil())
                .isEqualTo(LocalDateTime.of(2026, 9, 27, 4, 2).plusSeconds(30));
    }

    @Test
    void failedPublishBackoffStartsAfterThePublishFailure() {
        MutableClock clock = new MutableClock(CLOCK.instant());
        FakeRepository repository = new FakeRepository(List.of(candidate(4, 2)));
        repository.claimableIds.add(4L);
        RecordingPublisher publisher = new RecordingPublisher();
        publisher.beforeNextPublish = () -> clock.advance(Duration.ofSeconds(10));
        publisher.failure = new IllegalStateException("temporary publish failure");

        service(repository, publisher, new RecordingStatusPublisher(), clock).relayBatch();

        assertThat(repository.failures).singleElement().satisfies(failure ->
                assertThat(failure.retryAt()).isEqualTo(LocalDateTime.of(2026, 9, 27, 4, 0).plusSeconds(18)));
    }

    @Test
    void failedPublishUsesLegacyExponentialRetryDelayAndTruncatesError() {
        FakeRepository repository = new FakeRepository(List.of(candidate(4, 2)));
        repository.claimableIds.add(4L);
        RecordingPublisher publisher = new RecordingPublisher();
        publisher.failure = new IllegalStateException("x".repeat(700));

        int relayed = service(repository, publisher, new RecordingStatusPublisher()).relayBatch();

        assertThat(relayed).isZero();
        assertThat(repository.failures).hasSize(1);
        assertThat(repository.failures.get(0).error()).hasSize(500);
        assertThat(repository.failures.get(0).retryAt())
                .isEqualTo(LocalDateTime.of(2026, 9, 27, 4, 0).plusSeconds(8));
    }

    @Test
    void malformedOutboxPayloadIsRetriedInsteadOfMarkedSent() {
        FakeRepository repository = new FakeRepository(List.of(
                new FulfillmentOutboxCandidate(5, "FULFILLMENT_TASK_READY", "{not-json", 0)));
        repository.claimableIds.add(5L);

        int relayed = service(repository, new RecordingPublisher(), new RecordingStatusPublisher()).relayBatch();

        assertThat(relayed).isZero();
        assertThat(repository.sentIds).isEmpty();
        assertThat(repository.failures).hasSize(1);
        assertThat(repository.failures.get(0).error()).isNotBlank();
    }

    private FulfillmentOutboxRelayService service(
            FakeRepository repository, RecordingPublisher publisher, RecordingStatusPublisher statusPublisher
    ) {
        return service(repository, publisher, statusPublisher, CLOCK);
    }

    private FulfillmentOutboxRelayService service(
            FakeRepository repository, RecordingPublisher publisher,
            RecordingStatusPublisher statusPublisher, Clock clock
    ) {
        return new FulfillmentOutboxRelayService(
                repository, publisher, statusPublisher, new tools.jackson.databind.ObjectMapper(),
                25, Duration.ofSeconds(30), clock);
    }

    private static FulfillmentOutboxCandidate candidate(long id, int retryCount) {
        return candidate(id, "FULFILLMENT_TASK_READY", retryCount);
    }

    private static FulfillmentOutboxCandidate candidate(long id, String eventType, int retryCount) {
        String payload = FulfillmentStatusChangedPublisher.EVENT_TYPE.equals(eventType)
                ? "{\"eventId\":\"EVENT-" + id + "\",\"fulfillmentNo\":\"TASK-" + id
                + "\",\"shippingNo\":\"SHIP-" + id + "\",\"sourceType\":\"LOTTERY_BENEFIT\","
                + "\"sourceId\":\"BENEFIT-" + id + "\",\"status\":\"DELIVERED\","
                + "\"occurredAt\":\"2026-09-27T04:00:00\"}"
                : "{\"eventId\":\"EVENT-" + id + "\",\"fulfillmentNo\":\"TASK-" + id
                + "\",\"occurredAt\":\"2026-09-27T04:00:00\"}";
        return new FulfillmentOutboxCandidate(id, eventType, payload, retryCount);
    }

    private static final class FakeRepository implements FulfillmentOutboxRepository {
        private final List<FulfillmentOutboxCandidate> candidates;
        private final List<Long> claimableIds = new ArrayList<>();
        private final List<Claim> claims = new ArrayList<>();
        private final List<Long> sentIds = new ArrayList<>();
        private final List<Failure> failures = new ArrayList<>();

        private FakeRepository(List<FulfillmentOutboxCandidate> candidates) {
            this.candidates = candidates;
        }

        @Override
        public List<FulfillmentOutboxCandidate> findRelayCandidates(LocalDateTime now, int limit) {
            return candidates.stream().limit(limit).toList();
        }

        @Override
        public boolean claimForRelay(long id, String claimToken, LocalDateTime now, LocalDateTime claimUntil) {
            claims.add(new Claim(id, claimToken, now, claimUntil));
            return claimableIds.contains(id);
        }

        @Override
        public int markSent(long id, String claimToken, LocalDateTime sentAt) {
            sentIds.add(id);
            return 1;
        }

        @Override
        public int markFailed(long id, String claimToken, String error, LocalDateTime retryAt) {
            failures.add(new Failure(id, claimToken, error, retryAt));
            return 1;
        }
    }

    private static final class RecordingPublisher implements FulfillmentTaskReadyPublisher {
        private final List<FulfillmentTaskReadyEvent> events = new ArrayList<>();
        private RuntimeException failure;
        private Runnable beforeNextPublish;

        @Override
        public void publish(FulfillmentTaskReadyEvent event) {
            if (beforeNextPublish != null) {
                Runnable action = beforeNextPublish;
                beforeNextPublish = null;
                action.run();
            }
            if (failure != null) {
                throw failure;
            }
            events.add(event);
        }
    }

    private static final class RecordingStatusPublisher implements FulfillmentStatusChangedPublisher {
        private final List<FulfillmentStatusChangedEventV1> events = new ArrayList<>();

        @Override
        public void publish(FulfillmentStatusChangedEventV1 event) {
            events.add(event);
        }
    }

    private record Claim(long id, String claimToken, LocalDateTime now, LocalDateTime claimUntil) {
    }

    private record Failure(long id, String claimToken, String error, LocalDateTime retryAt) {
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initial) {
            instant = new AtomicReference<>(initial);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (ZoneOffset.UTC.equals(zone)) {
                return this;
            }
            return Clock.fixed(instant.get(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }

        private void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }
    }
}
