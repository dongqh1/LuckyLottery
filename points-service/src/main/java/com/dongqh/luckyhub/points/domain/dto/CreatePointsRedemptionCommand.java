package com.dongqh.luckyhub.domain.dto.points;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePointsRedemptionCommand(
        @NotBlank @Size(max = 64) String redemptionNo,
        @Positive Long skuId,
        @Positive Long offerId,
        @NotNull @Min(1) @Max(100) Integer quantity,
        @Positive Long addressId
) {
    @AssertTrue(message = "skuId和offerId必须且只能填写一个")
    public boolean isRedemptionSourceValid() {
        return (skuId == null) != (offerId == null);
    }
}
