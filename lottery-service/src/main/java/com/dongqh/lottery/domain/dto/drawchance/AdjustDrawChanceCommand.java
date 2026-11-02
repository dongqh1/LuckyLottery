package com.dongqh.lottery.domain.dto.drawchance;

import com.dongqh.lottery.enums.drawchance.DrawChanceDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdjustDrawChanceCommand(
        @NotBlank(message = "调整编号不能为空") @Size(max = 64, message = "调整编号不能超过64个字符") String adjustmentId,
        @NotNull(message = "调整方向不能为空") DrawChanceDirection direction,
        @NotNull(message = "调整数量不能为空") @Positive(message = "调整数量必须大于0") Long amount,
        @NotBlank(message = "调整原因不能为空") @Size(max = 255, message = "调整原因不能超过255个字符") String reason
) {
}
