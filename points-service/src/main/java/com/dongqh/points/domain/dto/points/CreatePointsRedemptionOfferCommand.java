package com.dongqh.points.domain.dto.points;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePointsRedemptionOfferCommand(
        @NotNull @Positive Long hmallItemId,
        @NotNull @Positive Long unitPoints,
        @NotNull @Min(1) @Max(1_000_000) Integer availableStock
) {
}
