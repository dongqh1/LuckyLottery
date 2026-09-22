package com.dongqh.luckyhub.coupon.vo;

import com.dongqh.luckyhub.coupon.enums.CouponScopeType;

import java.time.LocalDateTime;

public record ApplicableCouponView(
        String userCouponId,
        String couponName,
        Long thresholdCent,
        Long discountCent,
        CouponScopeType scopeType,
        String scopeValue,
        LocalDateTime validUntil,
        boolean stackableWithMembership
) {
}
