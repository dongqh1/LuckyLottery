package com.dongqh.lotteryservice.domain.vo.activityprize;

import com.dongqh.lotteryservice.enums.activityprize.ActivityStatus;

import java.time.LocalDateTime;

public record ActivityView(Long id, String activityName, String description, ActivityStatus status,
                          LocalDateTime startTime, LocalDateTime endTime,
                          Integer noWinWeight, Long createdBy, LocalDateTime createdAt,
                          LocalDateTime updatedAt) {
}
