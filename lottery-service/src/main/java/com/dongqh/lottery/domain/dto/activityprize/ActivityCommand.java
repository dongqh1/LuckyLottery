package com.dongqh.lottery.domain.dto.activityprize;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ActivityCommand(
        @NotBlank @Size(max = 100) String activityName,
        @Size(max = 1000) String description,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @NotNull @PositiveOrZero Integer noWinWeight
) {
}
