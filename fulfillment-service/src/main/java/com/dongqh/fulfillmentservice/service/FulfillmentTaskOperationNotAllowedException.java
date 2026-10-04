package com.dongqh.fulfillmentservice.service;

import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;

public class FulfillmentTaskOperationNotAllowedException extends RuntimeException {

    public FulfillmentTaskOperationNotAllowedException(String operation, FulfillmentStatus status) {
        super("履约任务状态不允许操作: " + operation + ", " + status);
    }
}
