package com.dongqh.lottery.domain.po.draworder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.lottery.enums.draworder.DrawOrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("lottery_draw_order")
public class DrawOrderRow {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("request_id")
    private String requestId;
    @TableField("user_id")
    private Long userId;
    @TableField("activity_id")
    private Long activityId;
    @TableField("draw_count")
    private Integer drawCount;
    @TableField("draw_date")
    private LocalDate drawDate;
    private DrawOrderStatus status;
    @TableField("fail_reason")
    private String failReason;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("completed_at")
    private LocalDateTime completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public Integer getDrawCount() { return drawCount; }
    public void setDrawCount(Integer drawCount) { this.drawCount = drawCount; }
    public LocalDate getDrawDate() { return drawDate; }
    public void setDrawDate(LocalDate drawDate) { this.drawDate = drawDate; }
    public DrawOrderStatus getStatus() { return status; }
    public void setStatus(DrawOrderStatus status) { this.status = status; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
