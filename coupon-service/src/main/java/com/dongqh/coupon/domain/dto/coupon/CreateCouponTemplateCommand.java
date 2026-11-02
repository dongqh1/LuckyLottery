package com.dongqh.coupon.domain.dto.coupon;

import com.dongqh.coupon.enums.coupon.CouponScopeType;
import com.dongqh.coupon.enums.coupon.CouponType;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record CreateCouponTemplateCommand(
        @NotBlank @Size(max=64) String templateCode,
        @NotBlank @Size(max=100) String templateName,
        @NotNull CouponType couponType,
        @NotNull @Min(0) Long thresholdCent,
        @NotNull @Positive Long discountCent,
        @Positive Long applicableProductId,
        CouponScopeType scopeType,
        @Positive Long hmallItemId,
        @Size(max=100) String hmallCategory,
        @Size(max=100) String hmallBrand,
        @NotNull LocalDateTime validFrom,
        @NotNull LocalDateTime validUntil,
        @NotNull @Min(1) @Max(100) Integer perUserLimit,
        @NotNull Boolean stackableWithMembership
) {
    public CreateCouponTemplateCommand(String templateCode, String templateName, CouponType couponType,
                                       Long thresholdCent, Long discountCent, Long applicableProductId,
                                       LocalDateTime validFrom, LocalDateTime validUntil, Integer perUserLimit,
                                       Boolean stackableWithMembership) {
        this(templateCode, templateName, couponType, thresholdCent, discountCent, applicableProductId,
                null, null, null, null, validFrom, validUntil, perUserLimit, stackableWithMembership);
    }

    public CreateCouponTemplateCommand(String templateCode, String templateName, CouponType couponType,
                                       Long thresholdCent, Long discountCent, Long applicableProductId,
                                       CouponScopeType scopeType, Long hmallItemId, String hmallCategory,
                                       LocalDateTime validFrom, LocalDateTime validUntil, Integer perUserLimit,
                                       Boolean stackableWithMembership) {
        this(templateCode, templateName, couponType, thresholdCent, discountCent, applicableProductId,
                scopeType, hmallItemId, hmallCategory, null,
                validFrom, validUntil, perUserLimit, stackableWithMembership);
    }
}
