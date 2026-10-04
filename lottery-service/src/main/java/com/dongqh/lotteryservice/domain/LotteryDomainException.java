package com.dongqh.lotteryservice.domain;

import com.dongqh.lotteryservice.enums.LotteryErrorCode;

public class LotteryDomainException extends RuntimeException {

    private final LotteryErrorCode errorCode;

    public LotteryDomainException(LotteryErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public LotteryErrorCode getErrorCode() {
        return errorCode;
    }
}
