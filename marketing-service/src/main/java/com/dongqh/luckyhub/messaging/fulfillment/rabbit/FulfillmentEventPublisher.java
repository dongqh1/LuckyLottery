package com.dongqh.luckyhub.messaging.fulfillment.rabbit;

import com.dongqh.luckyhub.messaging.fulfillment.event.FulfillmentTaskReadyEvent;

public interface FulfillmentEventPublisher {
    void publish(FulfillmentTaskReadyEvent event);
}
