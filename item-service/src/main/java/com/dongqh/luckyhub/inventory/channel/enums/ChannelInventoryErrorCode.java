package com.dongqh.luckyhub.domain.channel.inventory.enums;

import org.springframework.http.HttpStatus;

public enum ChannelInventoryErrorCode {
    INVENTORY_NOT_FOUND(46001, "库存不存在", HttpStatus.NOT_FOUND),
    INVENTORY_INSUFFICIENT(46002, "可用库存不足", HttpStatus.CONFLICT),
    INVENTORY_STATE_CONFLICT(46003, "库存状态冲突", HttpStatus.CONFLICT),
    INVENTORY_IDEMPOTENCY_CONFLICT(46004, "库存幂等参数冲突", HttpStatus.CONFLICT),
    INVENTORY_SKU_UNAVAILABLE(46005, "SKU不可用于库存配置", HttpStatus.BAD_REQUEST),
    INVENTORY_REQUEST_INVALID(46006, "库存请求参数不合法", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ChannelInventoryErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() { return code; }
    public String message() { return message; }
    public HttpStatus httpStatus() { return httpStatus; }
}
