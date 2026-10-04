package com.dongqh.lotteryservice.activityprize.domain;

public class ActivityPrizeException extends RuntimeException {

    private final ActivityPrizeErrorCode errorCode;

    public ActivityPrizeException(ActivityPrizeErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ActivityPrizeErrorCode getErrorCode() { return errorCode; }
}
