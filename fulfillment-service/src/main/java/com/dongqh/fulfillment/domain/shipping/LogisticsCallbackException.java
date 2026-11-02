package com.dongqh.fulfillment.domain.shipping;

public final class LogisticsCallbackException extends RuntimeException {
    private final LogisticsCallbackErrorCode errorCode;

    public LogisticsCallbackException(LogisticsCallbackErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public LogisticsCallbackErrorCode errorCode() {
        return errorCode;
    }
}
