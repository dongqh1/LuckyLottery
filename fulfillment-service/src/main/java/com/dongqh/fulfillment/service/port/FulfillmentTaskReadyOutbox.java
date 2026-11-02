package com.dongqh.fulfillment.service.port;

public interface FulfillmentTaskReadyOutbox {

    void appendTaskReady(String fulfillmentNo);
}
