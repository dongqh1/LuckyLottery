package com.dongqh.lotteryservice.activityprize.vo;

import com.dongqh.lotteryservice.activityprize.domain.ActivityStatus;

import java.time.LocalDateTime;

public record ActivityView(Long id, String activityName, String description, ActivityStatus status,
                          LocalDateTime startTime, LocalDateTime endTime, Integer dailyLimit,
                          Integer noWinWeight, Long createdBy, LocalDateTime createdAt,
                          LocalDateTime updatedAt) {
}
