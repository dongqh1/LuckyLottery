package com.dongqh.lotteryservice.drawchance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.lotteryservice.drawchance.enums.DrawChanceBusinessType;
import com.dongqh.lotteryservice.drawchance.enums.DrawChanceDirection;

import java.time.LocalDateTime;

@TableName("draw_chance_ledger")
public class DrawChanceLedgerRow {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("business_type")
    private DrawChanceBusinessType businessType;
    @TableField("business_id")
    private String businessId;
    private DrawChanceDirection direction;
    private Long amount;
    @TableField("available_after")
    private Long availableAfter;
    @TableField("reserved_after")
    private Long reservedAfter;
    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public DrawChanceBusinessType getBusinessType() { return businessType; }
    public void setBusinessType(DrawChanceBusinessType businessType) { this.businessType = businessType; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }
    public DrawChanceDirection getDirection() { return direction; }
    public void setDirection(DrawChanceDirection direction) { this.direction = direction; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public Long getAvailableAfter() { return availableAfter; }
    public void setAvailableAfter(Long availableAfter) { this.availableAfter = availableAfter; }
    public Long getReservedAfter() { return reservedAfter; }
    public void setReservedAfter(Long reservedAfter) { this.reservedAfter = reservedAfter; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
