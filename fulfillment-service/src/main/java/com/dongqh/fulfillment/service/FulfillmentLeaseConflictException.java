package com.dongqh.fulfillment.service;

public class FulfillmentLeaseConflictException extends RuntimeException {

    public FulfillmentLeaseConflictException(long taskId) {
        super("履约任务租约冲突: " + taskId);
    }
}
