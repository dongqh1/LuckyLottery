package com.dongqh.fulfillmentservice.service;

public class FulfillmentTaskNotFoundException extends RuntimeException {

    public FulfillmentTaskNotFoundException(String fulfillmentNo) {
        super("履约任务不存在: " + fulfillmentNo);
    }
}
