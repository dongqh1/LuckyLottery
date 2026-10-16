package com.dongqh.luckyhub.domain.vo.activity;

import com.dongqh.luckyhub.enums.activity.ActivityStatus;

import java.time.LocalDateTime;

public record ActivityView(
        Long id,
        String activityName,
        String description,
        ActivityStatus status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer noWinWeight,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
