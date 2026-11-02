package com.dongqh.lottery.domain.dto.draworder;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DrawCommand(
        @NotBlank @Size(max = 64) String requestId,
        @NotNull @Positive Long activityId,
        @NotNull Integer drawCount
) {
    @AssertTrue(message = "抽奖次数只能是1或10")
    public boolean isSupportedDrawCount() {
        return drawCount != null && (drawCount == 1 || drawCount == 10);
    }
}
