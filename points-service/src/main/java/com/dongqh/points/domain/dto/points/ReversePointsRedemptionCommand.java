package com.dongqh.points.domain.dto.points;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReversePointsRedemptionCommand(
        @NotBlank @Size(max = 64) String reversalNo,
        @NotBlank @Size(max = 500) String reason
) {
}
