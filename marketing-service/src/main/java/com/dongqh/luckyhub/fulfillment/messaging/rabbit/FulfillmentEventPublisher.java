package com.dongqh.luckyhub.fulfillment.messaging.rabbit;

import com.dongqh.luckyhub.fulfillment.messaging.event.FulfillmentTaskReadyEvent;

public interface FulfillmentEventPublisher {
    void publish(FulfillmentTaskReadyEvent event);
}
