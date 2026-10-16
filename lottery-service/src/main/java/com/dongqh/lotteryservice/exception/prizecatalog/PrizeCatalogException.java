package com.dongqh.lotteryservice.exception.prizecatalog;

import com.dongqh.lotteryservice.enums.prizecatalog.PrizeErrorCode;

public class PrizeCatalogException extends RuntimeException {
    private final PrizeErrorCode errorCode;

    public PrizeCatalogException(PrizeErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public PrizeErrorCode getErrorCode() { return errorCode; }
}
