package com.dongqh.membership.domain.po.membership;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.membership.enums.membership.MembershipCardType;

import java.time.LocalDateTime;

@TableName("membership_product")
public class MembershipProduct {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String productCode;
    private String productName;
    private String membershipLevel;
    private MembershipCardType cardType;
    private Integer durationDays;
    private Long priceCent;
    private Integer discountBasisPoints;
    private Integer dailyDrawBonus;
    private Integer pointsMultiplierBasisPoints;
    private Integer status;
    @TableField("sales_enabled")
    private Integer salesEnabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getMembershipLevel() { return membershipLevel; }
    public void setMembershipLevel(String membershipLevel) { this.membershipLevel = membershipLevel; }
    public MembershipCardType getCardType() { return cardType; }
    public void setCardType(MembershipCardType cardType) { this.cardType = cardType; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Long getPriceCent() { return priceCent; }
    public void setPriceCent(Long priceCent) { this.priceCent = priceCent; }
    public Integer getDiscountBasisPoints() { return discountBasisPoints; }
    public void setDiscountBasisPoints(Integer discountBasisPoints) { this.discountBasisPoints = discountBasisPoints; }
    public Integer getDailyDrawBonus() { return dailyDrawBonus; }
    public void setDailyDrawBonus(Integer dailyDrawBonus) { this.dailyDrawBonus = dailyDrawBonus; }
    public Integer getPointsMultiplierBasisPoints() { return pointsMultiplierBasisPoints; }
    public void setPointsMultiplierBasisPoints(Integer pointsMultiplierBasisPoints) { this.pointsMultiplierBasisPoints = pointsMultiplierBasisPoints; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getSalesEnabled() { return salesEnabled; }
    public void setSalesEnabled(Integer salesEnabled) { this.salesEnabled = salesEnabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
