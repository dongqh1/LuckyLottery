package com.dongqh.luckyhub.coupon.service.port;

/** User-service boundary; callers must fail closed when eligibility cannot be confirmed. */
public interface CouponUserEligibilityPort {
    boolean isEnabled(long userId);
}
