package com.dongqh.luckyhub.scheduler.fulfillment;

import com.dongqh.luckyhub.messaging.fulfillment.outbox.FulfillmentOutboxRelay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FulfillmentOutboxRelayScheduler {
    private final FulfillmentOutboxRelay relay;

    public FulfillmentOutboxRelayScheduler(FulfillmentOutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${luckyhub.fulfillment.outbox-interval:5s}",
            initialDelayString = "${luckyhub.fulfillment.outbox-initial-delay:60s}")
    public void relay() {
        relay.relayBatch();
    }
}
