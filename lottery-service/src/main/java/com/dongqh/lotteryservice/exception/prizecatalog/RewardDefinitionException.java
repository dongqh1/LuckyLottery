package com.dongqh.lotteryservice.exception.prizecatalog;

import com.dongqh.lotteryservice.enums.prizecatalog.RewardDefinitionErrorCode;

public class RewardDefinitionException extends RuntimeException {
    private final RewardDefinitionErrorCode errorCode;

    public RewardDefinitionException(RewardDefinitionErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public RewardDefinitionErrorCode getErrorCode() { return errorCode; }
}
