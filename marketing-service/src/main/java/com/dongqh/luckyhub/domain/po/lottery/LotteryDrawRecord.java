package com.dongqh.luckyhub.domain.po.lottery;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.luckyhub.enums.lottery.DrawResultType;
import com.dongqh.luckyhub.enums.prize.PrizeType;
import com.dongqh.luckyhub.enums.reward.RewardType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@TableName("lottery_draw_record")
public class LotteryDrawRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String requestId;
    private Integer sequenceNo;
    private Long userId;
    private Long activityId;
    private DrawResultType resultType;
    private Long prizeId;
    private String prizeName;
    private PrizeType prizeType;
    private String prizeImageUrl;
    private Long rewardDefinitionId;
    private RewardType rewardType;
    private Long rewardTargetId;
    private Long rewardQuantity;
    private String rewardPayload;
    private String rewardFingerprint;
    private LocalDateTime drawTime;

    @TableField(exist = false)
    private Long benefitId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
