package com.dongqh.lottery.outbox.service;

import com.dongqh.lottery.outbox.event.DrawEventEnvelope;
import com.dongqh.lottery.outbox.mapper.MessageOutboxMapper;
import com.dongqh.lottery.outbox.model.MessageOutbox;
import com.dongqh.lottery.outbox.publisher.DrawEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "luckyhub.lottery.outbox", name = "enabled", havingValue = "true")
public class OutboxRelayService {
    private static final int MAX_ERROR_LENGTH = 500;
    private final MessageOutboxMapper mapper;
    private final DrawEventPublisher publisher;
    private final ObjectMapper json;
    private final int batchSize;
    private final Duration leaseDuration;
    private final Clock clock;

    @Autowired
    public OutboxRelayService(MessageOutboxMapper mapper, DrawEventPublisher publisher, ObjectMapper json,
                              @Value("${luckyhub.lottery.outbox-batch-size:100}") int batchSize,
                              @Value("${luckyhub.lottery.outbox.lease:30s}") String leaseDuration) {
        this(mapper, publisher, json, batchSize, DurationStyle.detectAndParse(leaseDuration), Clock.systemDefaultZone());
    }

    public OutboxRelayService(MessageOutboxMapper mapper, DrawEventPublisher publisher, ObjectMapper json,
                              int batchSize, Duration leaseDuration, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.json = Objects.requireNonNull(json, "json must not be null");
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be positive");
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public int relayBatch() {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        List<MessageOutbox> rows = mapper.selectRelayCandidates(now, batchSize);
        int sent = 0;
        for (MessageOutbox row : rows) {
            String claimToken = UUID.randomUUID().toString();
            if (mapper.claimForRelay(row.getId(), claimToken, now, now.plus(leaseDuration)) != 1) continue;
            try {
                DrawEventEnvelope event = json.readValue(row.getPayload(), DrawEventEnvelope.class);
                publisher.publish(event);
                if (mapper.markSent(row.getId(), claimToken,
                        LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS)) == 1) sent++;
            } catch (Exception exception) {
                recordFailure(row, claimToken, now, exception);
            }
        }
        return sent;
    }

    @Scheduled(fixedDelayString = "${luckyhub.lottery.outbox-interval:5s}",
            initialDelayString = "${luckyhub.lottery.outbox-initial-delay:60s}")
    public void relayOutbox() {
        relayBatch();
    }

    private void recordFailure(MessageOutbox row, String claimToken, LocalDateTime now, Exception exception) {
        int retry = row.getRetryCount() == null ? 1 : row.getRetryCount() + 1;
        long delaySeconds = Math.min(300L, 1L << Math.min(retry, 8));
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName() : exception.getMessage();
        if (message.length() > MAX_ERROR_LENGTH) message = message.substring(0, MAX_ERROR_LENGTH);
        mapper.markFailed(row.getId(), claimToken, message, now.plusSeconds(delaySeconds));
    }
}
