package com.dongqh.luckyhub.coupon.vo;

import com.dongqh.luckyhub.coupon.enums.CouponScopeType;
import com.dongqh.luckyhub.coupon.enums.CouponType;
import java.time.LocalDateTime;

public record CouponTemplateView(
        Long id, String templateCode, String templateName, CouponType couponType,
        Long thresholdCent, Long discountCent, Long applicableProductId,
        CouponScopeType scopeType, Long hmallItemId, String hmallCategory, String hmallBrand,
        LocalDateTime validFrom, LocalDateTime validUntil, Integer perUserLimit,
        Boolean stackableWithMembership, Integer status
) {
    public CouponTemplateView(Long id, String templateCode, String templateName, CouponType couponType,
                              Long thresholdCent, Long discountCent, Long applicableProductId,
                              LocalDateTime validFrom, LocalDateTime validUntil, Integer perUserLimit,
                              Boolean stackableWithMembership, Integer status) {
        this(id, templateCode, templateName, couponType, thresholdCent, discountCent, applicableProductId,
                null, null, null, null, validFrom, validUntil, perUserLimit, stackableWithMembership, status);
    }
}
