package com.dongqh.luckyhub.domain.vo.coupon;

import com.dongqh.luckyhub.enums.coupon.CouponScopeType;

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
