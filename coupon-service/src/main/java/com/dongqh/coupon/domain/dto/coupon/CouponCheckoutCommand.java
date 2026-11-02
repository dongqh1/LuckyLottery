package com.dongqh.coupon.domain.dto.coupon;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CouponCheckoutCommand(
        @NotEmpty @Size(max = 100) List<@Valid CouponCheckoutItemCommand> items
) {
}
