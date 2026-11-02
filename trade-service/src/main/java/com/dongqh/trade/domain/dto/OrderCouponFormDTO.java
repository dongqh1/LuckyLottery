package com.dongqh.trade.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Coupon identifiers travel as decimal strings to avoid JavaScript long precision loss. */
@Data
public class OrderCouponFormDTO {
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    @DecimalMax("9223372036854775807")
    private String couponId;
}
