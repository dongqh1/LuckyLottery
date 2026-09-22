package com.hmall.domain.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMax;

/** Coupon identifiers travel as decimal strings to avoid JavaScript long precision loss. */
@Data
public class OrderCouponFormDTO {
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    @DecimalMax("9223372036854775807")
    private String couponId;
}
