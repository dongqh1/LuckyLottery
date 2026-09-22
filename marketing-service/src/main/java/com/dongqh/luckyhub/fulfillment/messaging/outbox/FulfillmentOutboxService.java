package com.dongqh.luckyhub.fulfillment.messaging.outbox;

public interface FulfillmentOutboxService {
    void appendTaskReady(String fulfillmentNo);
}
