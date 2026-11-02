package com.dongqh.fulfillment.service;

public class FulfillmentTaskOperationConflictException extends RuntimeException {

    public FulfillmentTaskOperationConflictException(long taskId) {
        super("履约任务状态更新冲突: " + taskId);
    }
}
