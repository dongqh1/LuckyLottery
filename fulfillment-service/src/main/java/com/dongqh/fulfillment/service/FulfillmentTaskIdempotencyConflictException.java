package com.dongqh.fulfillment.service;

public class FulfillmentTaskIdempotencyConflictException extends RuntimeException {

    public FulfillmentTaskIdempotencyConflictException(String fulfillmentNo) {
        super("履约幂等参数冲突: " + fulfillmentNo);
    }
}
