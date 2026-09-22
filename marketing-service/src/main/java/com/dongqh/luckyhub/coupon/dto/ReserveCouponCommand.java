package com.dongqh.luckyhub.coupon.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReserveCouponCommand(
        @Positive long couponId,
        @NotBlank @Size(max = 64) String orderNo,
        @NotEmpty @Size(max = 100) List<@Valid CouponCheckoutItemCommand> items
) {
}
