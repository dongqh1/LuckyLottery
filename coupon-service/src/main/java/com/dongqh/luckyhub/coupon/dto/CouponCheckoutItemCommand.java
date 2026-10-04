package com.dongqh.luckyhub.coupon.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record CouponCheckoutItemCommand(
        @Positive long itemId,
        @Min(1) int quantity
) {
}
