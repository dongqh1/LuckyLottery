package com.dongqh.lottery.domain.po.drawtransaction;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.lottery.enums.drawtransaction.DrawResultType;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;
import com.dongqh.lottery.enums.prizecatalog.RewardType;

import java.time.LocalDateTime;

@TableName("lottery_draw_record")
public class DrawRecordRow {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("order_id")
    private Long orderId;
    @TableField("request_id")
    private String requestId;
    @TableField("sequence_no")
    private Integer sequenceNo;
    @TableField("user_id")
    private Long userId;
    @TableField("activity_id")
    private Long activityId;
    @TableField("result_type")
    private DrawResultType resultType;
    @TableField("prize_id")
    private Long prizeId;
    @TableField(exist = false)
    private String prizeName;
    @TableField(exist = false)
    private PrizeType prizeType;
    @TableField(exist = false)
    private String prizeImageUrl;
    @TableField(exist = false)
    private Long rewardDefinitionId;
    @TableField(exist = false)
    private RewardType rewardType;
    @TableField(exist = false)
    private Long rewardTargetId;
    @TableField(exist = false)
    private Long rewardQuantity;
    @TableField("draw_time")
    private LocalDateTime drawTime;
    @TableField(exist = false)
    private Long benefitId;
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Integer getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Integer sequenceNo) { this.sequenceNo = sequenceNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public DrawResultType getResultType() { return resultType; }
    public void setResultType(DrawResultType resultType) { this.resultType = resultType; }
    public Long getPrizeId() { return prizeId; }
    public void setPrizeId(Long prizeId) { this.prizeId = prizeId; }
    public String getPrizeName() { return prizeName; }
    public void setPrizeName(String prizeName) { this.prizeName = prizeName; }
    public PrizeType getPrizeType() { return prizeType; }
    public void setPrizeType(PrizeType prizeType) { this.prizeType = prizeType; }
    public String getPrizeImageUrl() { return prizeImageUrl; }
    public void setPrizeImageUrl(String prizeImageUrl) { this.prizeImageUrl = prizeImageUrl; }
    public Long getRewardDefinitionId() { return rewardDefinitionId; }
    public void setRewardDefinitionId(Long rewardDefinitionId) { this.rewardDefinitionId = rewardDefinitionId; }
    public RewardType getRewardType() { return rewardType; }
    public void setRewardType(RewardType rewardType) { this.rewardType = rewardType; }
    public Long getRewardTargetId() { return rewardTargetId; }
    public void setRewardTargetId(Long rewardTargetId) { this.rewardTargetId = rewardTargetId; }
    public Long getRewardQuantity() { return rewardQuantity; }
    public void setRewardQuantity(Long rewardQuantity) { this.rewardQuantity = rewardQuantity; }
    public LocalDateTime getDrawTime() { return drawTime; }
    public void setDrawTime(LocalDateTime drawTime) { this.drawTime = drawTime; }
    public Long getBenefitId() { return benefitId; }
    public void setBenefitId(Long benefitId) { this.benefitId = benefitId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
