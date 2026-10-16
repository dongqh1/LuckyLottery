package com.dongqh.luckyhub.messaging.fulfillment.outbox;

public interface FulfillmentOutboxService {
    void appendTaskReady(String fulfillmentNo);
}
