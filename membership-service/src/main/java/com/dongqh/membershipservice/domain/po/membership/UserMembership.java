package com.dongqh.membershipservice.domain.po.membership;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_membership")
public class UserMembership {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String membershipLevel;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private Integer discountBasisPoints;
    private Integer dailyDrawBonus;
    private Integer pointsMultiplierBasisPoints;
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getMembershipLevel() { return membershipLevel; }
    public void setMembershipLevel(String membershipLevel) { this.membershipLevel = membershipLevel; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public Integer getDiscountBasisPoints() { return discountBasisPoints; }
    public void setDiscountBasisPoints(Integer discountBasisPoints) { this.discountBasisPoints = discountBasisPoints; }
    public Integer getDailyDrawBonus() { return dailyDrawBonus; }
    public void setDailyDrawBonus(Integer dailyDrawBonus) { this.dailyDrawBonus = dailyDrawBonus; }
    public Integer getPointsMultiplierBasisPoints() { return pointsMultiplierBasisPoints; }
    public void setPointsMultiplierBasisPoints(Integer pointsMultiplierBasisPoints) { this.pointsMultiplierBasisPoints = pointsMultiplierBasisPoints; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
