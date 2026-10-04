package com.dongqh.fulfillmentservice.shipping.persistence;

public final class DuplicateShippingAddressSnapshotException extends RuntimeException {
    public DuplicateShippingAddressSnapshotException(Throwable cause) {
        super("地址快照唯一键冲突", cause);
    }
}
