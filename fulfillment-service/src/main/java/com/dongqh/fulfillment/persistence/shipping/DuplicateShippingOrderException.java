package com.dongqh.fulfillment.persistence.shipping;

public final class DuplicateShippingOrderException extends RuntimeException {
    public DuplicateShippingOrderException(Throwable cause) {
        super("发货单唯一键冲突", cause);
    }
}
