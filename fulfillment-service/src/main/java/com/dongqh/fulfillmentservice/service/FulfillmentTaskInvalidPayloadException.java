package com.dongqh.fulfillmentservice.service;

public class FulfillmentTaskInvalidPayloadException extends RuntimeException {

    public FulfillmentTaskInvalidPayloadException(String fulfillmentNo, Throwable cause) {
        super("履约任务快照无法解析: " + fulfillmentNo, cause);
    }
}
