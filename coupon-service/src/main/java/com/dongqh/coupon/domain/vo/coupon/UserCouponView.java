package com.dongqh.coupon.domain.vo.coupon;

import com.dongqh.coupon.enums.coupon.CouponScopeType;
import com.dongqh.coupon.enums.coupon.CouponType;
import com.dongqh.coupon.enums.coupon.UserCouponStatus;

import java.time.LocalDateTime;

public record UserCouponView(
        Long id,
        String couponNo,
        Long templateId,
        String templateName,
        CouponType couponType,
        Long thresholdCent,
        Long discountCent,
        CouponScopeType scopeType,
        Long hmallItemId,
        String hmallCategory,
        String hmallBrand,
        Long userId,
        UserCouponStatus status,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        String lockedOrderNo,
        String usedOrderNo
) {
}
