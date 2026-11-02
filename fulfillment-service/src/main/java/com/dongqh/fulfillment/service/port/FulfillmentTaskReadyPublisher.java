package com.dongqh.fulfillment.service.port;

import com.dongqh.fulfillment.messaging.FulfillmentTaskReadyEvent;

public interface FulfillmentTaskReadyPublisher {

    void publish(FulfillmentTaskReadyEvent event);
}
