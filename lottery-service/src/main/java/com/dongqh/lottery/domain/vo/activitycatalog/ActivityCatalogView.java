package com.dongqh.lottery.domain.vo.activitycatalog;

import com.dongqh.lottery.enums.activityprize.ActivityStatus;

import java.time.LocalDateTime;

public record ActivityCatalogView(Long id, String activityName, String description, ActivityStatus status,
                                  LocalDateTime startTime, LocalDateTime endTime) {
}
