package com.dongqh.fulfillmentservice.service;

import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.model.CouponFulfillmentPayload;
import com.dongqh.fulfillmentservice.model.CreateFulfillmentTaskCommand;
import com.dongqh.fulfillmentservice.model.FulfillmentTaskRecord;
import com.dongqh.fulfillmentservice.persistence.DuplicateFulfillmentTaskException;
import com.dongqh.fulfillmentservice.persistence.FulfillmentTaskRepository;
import com.dongqh.fulfillmentservice.service.port.FulfillmentTaskReadyOutbox;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FulfillmentTaskCreationServiceTests {

    private final InMemoryRepository repository = new InMemoryRepository();
    private final RecordingOutbox outbox = new RecordingOutbox();
    private final FulfillmentTaskCreationService service = new FulfillmentTaskCreationService(
            repository,
            outbox,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-09-27T04:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void createsPendingTaskAndAppendsOneReadyEvent() {
        FulfillmentTaskRecord task = service.create(command("TASK-1", "SUMMER"));

        assertThat(task.fulfillmentNo()).isEqualTo("TASK-1");
        assertThat(task.status()).isEqualTo(FulfillmentStatus.PENDING);
        assertThat(task.attemptCount()).isZero();
        assertThat(task.maxAttempts()).isEqualTo(5);
        assertThat(task.nextAttemptAt()).isEqualTo(LocalDateTime.of(2026, 9, 27, 4, 0));
        assertThat(task.requestPayload()).contains("SUMMER");
        assertThat(task.requestFingerprint()).matches("[0-9a-f]{64}");
        assertThat(outbox.fulfillmentNumbers).containsExactly("TASK-1");
    }

    @Test
    void identicalCommandReplayReturnsExistingTaskWithoutDuplicatingEvent() {
        FulfillmentTaskRecord first = service.create(command("TASK-2", "SUMMER"));

        FulfillmentTaskRecord replay = service.create(command("TASK-2", "SUMMER"));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.requestFingerprint()).isEqualTo(first.requestFingerprint());
        assertThat(outbox.fulfillmentNumbers).containsExactly("TASK-2");
    }

    @Test
    void conflictingCommandReplayIsRejectedWithoutAppendingEvent() {
        service.create(command("TASK-3", "SUMMER"));

        assertThatThrownBy(() -> service.create(command("TASK-3", "WINTER")))
                .isInstanceOf(FulfillmentTaskIdempotencyConflictException.class);
        assertThat(outbox.fulfillmentNumbers).containsExactly("TASK-3");
    }

    private CreateFulfillmentTaskCommand command(String fulfillmentNo, String templateCode) {
        return new CreateFulfillmentTaskCommand(
                fulfillmentNo,
                "LOTTERY_REWARD",
                "DRAW-1",
                FulfillmentType.COUPON,
                11L,
                new CouponFulfillmentPayload(templateCode, 1),
                null
        );
    }

    private static final class InMemoryRepository implements FulfillmentTaskRepository {
        private final Map<String, FulfillmentTaskRecord> tasks = new HashMap<>();
        private final AtomicLong ids = new AtomicLong(100);

        @Override
        public Optional<FulfillmentTaskRecord> findByFulfillmentNo(String fulfillmentNo) {
            return Optional.ofNullable(tasks.get(fulfillmentNo));
        }

        @Override
        public FulfillmentTaskRecord insert(FulfillmentTaskRecord task) {
            if (tasks.containsKey(task.fulfillmentNo())) {
                throw new DuplicateFulfillmentTaskException(task.fulfillmentNo());
            }
            FulfillmentTaskRecord stored = task.withId(ids.incrementAndGet());
            tasks.put(task.fulfillmentNo(), stored);
            return stored;
        }
    }

    private static final class RecordingOutbox implements FulfillmentTaskReadyOutbox {
        private final java.util.List<String> fulfillmentNumbers = new java.util.ArrayList<>();

        @Override
        public void appendTaskReady(String fulfillmentNo) {
            fulfillmentNumbers.add(fulfillmentNo);
        }
    }
}
