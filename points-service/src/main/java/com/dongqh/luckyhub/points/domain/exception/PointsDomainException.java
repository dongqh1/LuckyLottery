package com.dongqh.luckyhub.points.domain.exception;

import com.dongqh.luckyhub.enums.points.PointsErrorCode;

public class PointsDomainException extends RuntimeException {

    private final PointsErrorCode errorCode;

    public PointsDomainException(PointsErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public PointsErrorCode errorCode() {
        return errorCode;
    }
}
