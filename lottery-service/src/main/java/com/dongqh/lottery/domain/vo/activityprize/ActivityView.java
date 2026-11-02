package com.dongqh.lottery.domain.vo.activityprize;

import com.dongqh.lottery.enums.activityprize.ActivityStatus;

import java.time.LocalDateTime;

public record ActivityView(Long id, String activityName, String description, ActivityStatus status,
                          LocalDateTime startTime, LocalDateTime endTime,
                          Integer noWinWeight, Long createdBy, LocalDateTime createdAt,
                          LocalDateTime updatedAt) {
}
