package com.dongqh.luckyhub.inventory.channel.exception;

import com.dongqh.luckyhub.inventory.channel.enums.ChannelInventoryErrorCode;

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
