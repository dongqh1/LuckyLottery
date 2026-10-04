package com.dongqh.lotteryservice.activitycatalog.entity;

import com.dongqh.lotteryservice.activityprize.domain.ActivityStatus;

import java.time.LocalDateTime;

public class ActivityCatalogRow {
    private Long id;
    private String activityName;
    private String description;
    private ActivityStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer dailyLimit;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getActivityName() { return activityName; }
    public void setActivityName(String activityName) { this.activityName = activityName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ActivityStatus getStatus() { return status; }
    public void setStatus(ActivityStatus status) { this.status = status; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(Integer dailyLimit) { this.dailyLimit = dailyLimit; }
}
