package com.dongqh.luckyhub.coupon.exception;

import com.dongqh.luckyhub.coupon.enums.CouponErrorCode;

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
