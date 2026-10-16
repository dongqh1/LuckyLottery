package com.dongqh.luckyhub.domain.dto.coupon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CouponOrderBindingCommand(
        @NotBlank @Size(max = 64) String orderNo
) {
}
