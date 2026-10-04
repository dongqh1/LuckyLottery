package com.dongqh.lotteryservice.prizecatalog.domain;

public class PrizeCatalogException extends RuntimeException {
    private final PrizeErrorCode errorCode;

    public PrizeCatalogException(PrizeErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public PrizeErrorCode getErrorCode() { return errorCode; }
}
