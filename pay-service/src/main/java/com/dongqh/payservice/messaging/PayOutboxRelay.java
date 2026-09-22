package com.dongqh.payservice.messaging;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.payservice.mapper.PayMessageOutboxMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "hm.messaging", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class PayOutboxRelay {
    private final PayMessageOutboxMapper mapper;
    private final PaySuccessPublisher publisher;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final Duration lease;

    public PayOutboxRelay(PayMessageOutboxMapper mapper, PaySuccessPublisher publisher,
                          ObjectMapper objectMapper,
                          @Value("${hm.messaging.outbox-batch-size:100}") int batchSize,
                          @Value("${hm.messaging.outbox-lease:30s}") Duration lease) {
        this.mapper = mapper;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.lease = lease;
    }

    @Scheduled(fixedDelayString = "${hm.messaging.outbox-interval:1s}",
            initialDelayString = "${hm.messaging.outbox-initial-delay:10s}")
    public void relay() {
        LocalDateTime now = LocalDateTime.now();
        for (PayMessageOutbox row : mapper.selectRelayCandidates(now, batchSize)) {
            String token = UUID.randomUUID().toString();
            if (mapper.claim(row.getId(), token, now, now.plus(lease)) != 1) {
                continue;
            }
            try {
                publisher.publish(objectMapper.readValue(row.getPayload(), PaySuccessMessage.class));
                mapper.markSent(row.getId(), token, LocalDateTime.now());
            } catch (Exception failure) {
                int retry = row.getRetryCount() == null ? 1 : row.getRetryCount() + 1;
                long delay = Math.min(300, 1L << Math.min(retry, 8));
                String error = failure.getMessage() == null
                        ? failure.getClass().getSimpleName() : failure.getMessage();
                mapper.markFailed(row.getId(), token,
                        error.substring(0, Math.min(500, error.length())),
                        now.plusSeconds(delay));
            }
        }
    }
}
