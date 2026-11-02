package com.dongqh.fulfillment.service;

import com.dongqh.fulfillment.messaging.FulfillmentTaskReadyEvent;
import com.dongqh.fulfillment.persistence.FulfillmentOutboxCandidate;
import com.dongqh.fulfillment.persistence.FulfillmentOutboxRepository;
import com.dongqh.fulfillment.service.port.FulfillmentStatusChangedPublisher;
import com.dongqh.fulfillment.service.port.FulfillmentTaskReadyPublisher;
import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

public class FulfillmentOutboxRelayService {

    private final FulfillmentOutboxRepository repository;
    private final FulfillmentTaskReadyPublisher taskReadyPublisher;
    private final FulfillmentStatusChangedPublisher statusChangedPublisher;
    private final ObjectMapper json;
    private final int batchSize;
    private final Duration leaseDuration;
    private final Clock clock;

    public FulfillmentOutboxRelayService(
            FulfillmentOutboxRepository repository,
            FulfillmentTaskReadyPublisher taskReadyPublisher,
            FulfillmentStatusChangedPublisher statusChangedPublisher,
            ObjectMapper json,
            int batchSize,
            Duration leaseDuration,
            Clock fulfillmentClock
    ) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("outbox batchSize must be positive");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("outbox leaseDuration must be positive");
        }
        this.repository = repository;
        this.taskReadyPublisher = taskReadyPublisher;
        this.statusChangedPublisher = statusChangedPublisher;
        this.json = json;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
        this.clock = fulfillmentClock;
    }

    public int relayBatch() {
        LocalDateTime scanStartedAt = LocalDateTime.now(clock);
        int sent = 0;
        for (FulfillmentOutboxCandidate row : repository.findRelayCandidates(scanStartedAt, batchSize)) {
            String token = UUID.randomUUID().toString();
            LocalDateTime claimedAt = LocalDateTime.now(clock);
            if (!repository.claimForRelay(row.id(), token, claimedAt, claimedAt.plus(leaseDuration))) {
                continue;
            }
            try {
                dispatch(row);
                sent += repository.markSent(row.id(), token, LocalDateTime.now(clock));
            } catch (Exception exception) {
                long retry = row.retryCount() + 1;
                long delaySeconds = Math.min(300L, 1L << Math.min(retry, 8L));
                LocalDateTime failedAt = LocalDateTime.now(clock);
                String error = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                repository.markFailed(
                        row.id(),
                        token,
                        truncate(error, 500),
                        failedAt.plusSeconds(delaySeconds)
                );
            }
        }
        return sent;
    }

    @Scheduled(fixedDelayString = "${luckyhub.fulfillment.outbox-interval:1s}",
            initialDelayString = "${luckyhub.fulfillment.outbox-initial-delay:0s}")
    public void relay() {
        relayBatch();
    }

    private void dispatch(FulfillmentOutboxCandidate row) throws Exception {
        if ("FULFILLMENT_TASK_READY".equals(row.eventType())) {
            taskReadyPublisher.publish(json.readValue(row.payload(), FulfillmentTaskReadyEvent.class));
            return;
        }
        if (FulfillmentStatusChangedPublisher.EVENT_TYPE.equals(row.eventType())) {
            statusChangedPublisher.publish(json.readValue(row.payload(), FulfillmentStatusChangedEventV1.class));
            return;
        }
        throw new IllegalArgumentException("unsupported fulfillment outbox event type");
    }

    private String truncate(String value, int maxLength) {
        return value.substring(0, Math.min(maxLength, value.length()));
    }
}
