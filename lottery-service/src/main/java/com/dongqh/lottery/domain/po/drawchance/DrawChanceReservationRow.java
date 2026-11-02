package com.dongqh.lottery.domain.po.drawchance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.lottery.enums.drawchance.DrawChanceReservationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("draw_chance_reservation")
public class DrawChanceReservationRow {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("request_id")
    private String requestId;
    @TableField("user_id")
    private Long userId;
    @TableField("activity_id")
    private Long activityId;
    @TableField("draw_date")
    private LocalDate drawDate;
    @TableField("draw_count")
    private Integer drawCount;
    @TableField("bonus_reserved")
    private Long bonusReserved;
    private DrawChanceReservationStatus status;
    @TableField("settled_at")
    private LocalDateTime settledAt;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public LocalDate getDrawDate() { return drawDate; }
    public void setDrawDate(LocalDate drawDate) { this.drawDate = drawDate; }
    public Integer getDrawCount() { return drawCount; }
    public void setDrawCount(Integer drawCount) { this.drawCount = drawCount; }
    public Long getBonusReserved() { return bonusReserved; }
    public void setBonusReserved(Long bonusReserved) { this.bonusReserved = bonusReserved; }
    public DrawChanceReservationStatus getStatus() { return status; }
    public void setStatus(DrawChanceReservationStatus status) { this.status = status; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
