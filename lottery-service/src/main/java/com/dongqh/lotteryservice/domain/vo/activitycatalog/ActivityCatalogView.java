package com.dongqh.lotteryservice.domain.vo.activitycatalog;

import com.dongqh.lotteryservice.enums.activityprize.ActivityStatus;

import java.time.LocalDateTime;

public record ActivityCatalogView(Long id, String activityName, String description, ActivityStatus status,
                                  LocalDateTime startTime, LocalDateTime endTime) {
}
