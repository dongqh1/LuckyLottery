package com.dongqh.points.domain.exception;

import com.dongqh.points.enums.points.PointsErrorCode;

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
