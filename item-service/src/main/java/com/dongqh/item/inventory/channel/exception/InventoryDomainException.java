package com.dongqh.item.inventory.channel.exception;

import com.dongqh.item.domain.channel.inventory.enums.ChannelInventoryErrorCode;

public final class InventoryDomainException extends RuntimeException {

    private final ChannelInventoryErrorCode errorCode;

    public InventoryDomainException(ChannelInventoryErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public ChannelInventoryErrorCode errorCode() {
        return errorCode;
    }
}
