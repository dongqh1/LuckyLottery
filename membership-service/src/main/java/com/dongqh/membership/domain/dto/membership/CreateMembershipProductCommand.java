package com.dongqh.membership.domain.dto.membership;

import com.dongqh.membership.enums.membership.MembershipCardType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateMembershipProductCommand(
        @NotBlank @Size(max = 64) String productCode,
        @NotBlank @Size(max = 100) String productName,
        @NotBlank @Size(max = 30) String membershipLevel,
        @NotNull MembershipCardType cardType,
        @NotNull @Positive Integer durationDays,
        @NotNull @Min(0) Long priceCent,
        @NotNull @Min(1) @Max(10_000) Integer discountBasisPoints,
        @NotNull @Min(0) Integer dailyDrawBonus,
        @NotNull @Min(10_000) Integer pointsMultiplierBasisPoints
) {
}
