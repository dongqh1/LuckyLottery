package com.dongqh.fulfillment.persistence;

public class DuplicateFulfillmentTaskException extends RuntimeException {

    public DuplicateFulfillmentTaskException(String fulfillmentNo, Throwable cause) {
        super("履约任务已存在: " + fulfillmentNo, cause);
    }

    public DuplicateFulfillmentTaskException(String fulfillmentNo) {
        super("履约任务已存在: " + fulfillmentNo);
    }
}
