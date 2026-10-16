package com.dongqh.luckyhub.coupon.domain.exception;

import com.dongqh.luckyhub.enums.coupon.CouponErrorCode;

public class CouponDomainException extends RuntimeException {
    private final CouponErrorCode errorCode;

    public CouponDomainException(CouponErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public CouponErrorCode errorCode() {
        return errorCode;
    }
}
