package com.dongqh.lottery.domain.dto.activityprize;

import com.dongqh.lottery.enums.activityprize.ActivityStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class ActivityQuery {
    @Min(1)
    private long page = 1;
    @Min(1) @Max(100)
    private long size = 20;
    private String name;
    private ActivityStatus status;

    public long getPage() { return page; }
    public void setPage(long page) { this.page = page; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ActivityStatus getStatus() { return status; }
    public void setStatus(ActivityStatus status) { this.status = status; }
}
