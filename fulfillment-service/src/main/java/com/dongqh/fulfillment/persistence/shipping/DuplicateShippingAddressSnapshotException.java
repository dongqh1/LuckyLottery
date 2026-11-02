package com.dongqh.fulfillment.persistence.shipping;

public final class DuplicateShippingAddressSnapshotException extends RuntimeException {
    public DuplicateShippingAddressSnapshotException(Throwable cause) {
        super("地址快照唯一键冲突", cause);
    }
}
