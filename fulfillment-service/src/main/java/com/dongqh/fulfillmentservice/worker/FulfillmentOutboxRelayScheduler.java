package com.dongqh.fulfillmentservice.worker;

import com.dongqh.fulfillmentservice.service.FulfillmentOutboxRelayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FulfillmentOutboxRelayScheduler {
    private final FulfillmentOutboxRelayService relay;

    public FulfillmentOutboxRelayScheduler(FulfillmentOutboxRelayService relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${luckyhub.fulfillment.outbox-interval:1s}",
            initialDelayString = "${luckyhub.fulfillment.outbox-initial-delay:0s}")
    public void relay() {
        relay.relayBatch();
    }
}
