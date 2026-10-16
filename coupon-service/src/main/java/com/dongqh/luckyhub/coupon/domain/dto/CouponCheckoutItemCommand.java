package com.dongqh.luckyhub.domain.dto.coupon;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record CouponCheckoutItemCommand(
        @Positive long itemId,
        @Min(1) int quantity
) {
}
