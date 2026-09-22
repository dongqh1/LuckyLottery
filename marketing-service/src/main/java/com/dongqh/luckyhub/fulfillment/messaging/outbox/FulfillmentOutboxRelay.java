package com.dongqh.luckyhub.fulfillment.messaging.outbox;

import com.dongqh.luckyhub.fulfillment.config.FulfillmentProperties;
import com.dongqh.luckyhub.fulfillment.messaging.event.FulfillmentTaskReadyEvent;
import com.dongqh.luckyhub.fulfillment.mapper.FulfillmentOutboxMapper;
import com.dongqh.luckyhub.fulfillment.messaging.rabbit.FulfillmentEventPublisher;
import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FulfillmentOutboxRelay {
    private final FulfillmentOutboxMapper mapper;
    private final FulfillmentEventPublisher publisher;
    private final ObjectMapper json;
    private final int batchSize;
    private final Duration leaseDuration;

    @Autowired
    public FulfillmentOutboxRelay(FulfillmentOutboxMapper mapper, FulfillmentEventPublisher publisher,
                                  ObjectMapper json, FulfillmentProperties properties,
                                  MessagingProperties messaging) {
        this(mapper, publisher, json, properties.outboxBatchSize(), messaging.outboxLease());
    }

    public FulfillmentOutboxRelay(FulfillmentOutboxMapper mapper, FulfillmentEventPublisher publisher,
                                  ObjectMapper json, int batchSize, Duration leaseDuration) {
        this.mapper = mapper;
        this.publisher = publisher;
        this.json = json;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
    }

    public int relayBatch() {
        LocalDateTime now = LocalDateTime.now();
        int sent = 0;
        for (FulfillmentOutbox row : mapper.selectRelayCandidates(now, batchSize)) {
            String token = UUID.randomUUID().toString();
            if (mapper.claimForRelay(row.getId(), token, now, now.plus(leaseDuration)) != 1) {
                continue;
            }
            try {
                publisher.publish(json.readValue(row.getPayload(), FulfillmentTaskReadyEvent.class));
                sent += mapper.markSent(row.getId(), token, LocalDateTime.now());
            } catch (Exception exception) {
                int retry = row.getRetryCount() == null ? 1 : row.getRetryCount() + 1;
                long seconds = Math.min(300L, 1L << Math.min(retry, 8));
                String error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                mapper.markFailed(row.getId(), token,
                        error.substring(0, Math.min(500, error.length())), now.plusSeconds(seconds));
            }
        }
        return sent;
    }
}
