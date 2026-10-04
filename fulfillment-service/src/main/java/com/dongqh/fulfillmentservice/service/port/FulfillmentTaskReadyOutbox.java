package com.dongqh.fulfillmentservice.service.port;

public interface FulfillmentTaskReadyOutbox {

    void appendTaskReady(String fulfillmentNo);
}
