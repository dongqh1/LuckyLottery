package com.dongqh.lottery.exception.prizecatalog;

import com.dongqh.lottery.enums.prizecatalog.RewardDefinitionErrorCode;

public class RewardDefinitionException extends RuntimeException {
    private final RewardDefinitionErrorCode errorCode;

    public RewardDefinitionException(RewardDefinitionErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public RewardDefinitionErrorCode getErrorCode() { return errorCode; }
}
