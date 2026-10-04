package com.dongqh.lotteryservice.activitycatalog.vo;

import com.dongqh.lotteryservice.activityprize.domain.ActivityStatus;

import java.time.LocalDateTime;

public record ActivityCatalogView(Long id, String activityName, String description, ActivityStatus status,
                                  LocalDateTime startTime, LocalDateTime endTime, Integer dailyLimit) {
}
