package com.dongqh.fulfillmentservice.service.port;

import com.dongqh.fulfillmentservice.messaging.FulfillmentTaskReadyEvent;

public interface FulfillmentTaskReadyPublisher {

    void publish(FulfillmentTaskReadyEvent event);
}
