package com.dongqh.lotteryservice.drawtransaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.lotteryservice.drawtransaction.enums.BenefitStatus;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeType;
import com.dongqh.lotteryservice.prizecatalog.domain.RewardType;

import java.time.LocalDateTime;

@TableName("user_benefit")
public class UserBenefitRow {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("draw_record_id")
    private Long drawRecordId;
    @TableField("user_id")
    private Long userId;
    @TableField("prize_id")
    private Long prizeId;
    @TableField("prize_type")
    private PrizeType prizeType;
    @TableField("reward_definition_id")
    private Long rewardDefinitionId;
    @TableField("reward_type")
    private RewardType rewardType;
    @TableField("reward_target_id")
    private Long rewardTargetId;
    @TableField("reward_quantity")
    private Long rewardQuantity;
    @TableField("reward_payload")
    private String rewardPayload;
    @TableField("reward_fingerprint")
    private String rewardFingerprint;
    @TableField("fulfillment_no")
    private String fulfillmentNo;
    @TableField("quantity")
    private Integer quantity;
    @TableField("status")
    private BenefitStatus status;
    @TableField("grant_error")
    private String grantError;
    @TableField("obtained_at")
    private LocalDateTime obtainedAt;
    @TableField("expire_at")
    private LocalDateTime expireAt;
    @TableField("claim_deadline")
    private LocalDateTime claimDeadline;
    @TableField("claimed_at")
    private LocalDateTime claimedAt;
    @TableField("shipping_order_id")
    private Long shippingOrderId;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDrawRecordId() { return drawRecordId; }
    public void setDrawRecordId(Long drawRecordId) { this.drawRecordId = drawRecordId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPrizeId() { return prizeId; }
    public void setPrizeId(Long prizeId) { this.prizeId = prizeId; }
    public PrizeType getPrizeType() { return prizeType; }
    public void setPrizeType(PrizeType prizeType) { this.prizeType = prizeType; }
    public Long getRewardDefinitionId() { return rewardDefinitionId; }
    public void setRewardDefinitionId(Long rewardDefinitionId) { this.rewardDefinitionId = rewardDefinitionId; }
    public RewardType getRewardType() { return rewardType; }
    public void setRewardType(RewardType rewardType) { this.rewardType = rewardType; }
    public Long getRewardTargetId() { return rewardTargetId; }
    public void setRewardTargetId(Long rewardTargetId) { this.rewardTargetId = rewardTargetId; }
    public Long getRewardQuantity() { return rewardQuantity; }
    public void setRewardQuantity(Long rewardQuantity) { this.rewardQuantity = rewardQuantity; }
    public String getRewardPayload() { return rewardPayload; }
    public void setRewardPayload(String rewardPayload) { this.rewardPayload = rewardPayload; }
    public String getRewardFingerprint() { return rewardFingerprint; }
    public void setRewardFingerprint(String rewardFingerprint) { this.rewardFingerprint = rewardFingerprint; }
    public String getFulfillmentNo() { return fulfillmentNo; }
    public void setFulfillmentNo(String fulfillmentNo) { this.fulfillmentNo = fulfillmentNo; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BenefitStatus getStatus() { return status; }
    public void setStatus(BenefitStatus status) { this.status = status; }
    public String getGrantError() { return grantError; }
    public void setGrantError(String grantError) { this.grantError = grantError; }
    public LocalDateTime getObtainedAt() { return obtainedAt; }
    public void setObtainedAt(LocalDateTime obtainedAt) { this.obtainedAt = obtainedAt; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public LocalDateTime getClaimDeadline() { return claimDeadline; }
    public void setClaimDeadline(LocalDateTime claimDeadline) { this.claimDeadline = claimDeadline; }
    public LocalDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(LocalDateTime claimedAt) { this.claimedAt = claimedAt; }
    public Long getShippingOrderId() { return shippingOrderId; }
    public void setShippingOrderId(Long shippingOrderId) { this.shippingOrderId = shippingOrderId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
