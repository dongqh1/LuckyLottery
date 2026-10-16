package com.dongqh.lotteryservice.domain.dto.benefit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ClaimPhysicalBenefitCommand(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String requestId,
        @NotNull @Positive Long addressId
) {
}
