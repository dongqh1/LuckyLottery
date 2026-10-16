package com.dongqh.luckyhub.domain.model.lottery;

import com.dongqh.luckyhub.enums.benefit.BenefitStatus;
import com.dongqh.luckyhub.enums.lottery.DrawOrderStatus;
import com.dongqh.luckyhub.enums.prize.PrizeType;
import com.dongqh.luckyhub.enums.reward.RewardType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LotteryRewardIdentityRow {
    private Long benefitId;
    private Long drawRecordId;
    private Long orderId;
    private String requestId;
    private Long orderUserId;
    private Long recordUserId;
    private Long benefitUserId;
    private Long orderActivityId;
    private Long recordActivityId;
    private Long orderPrizeId;
    private Long recordPrizeId;
    private Long benefitPrizeId;
    private PrizeType recordPrizeType;
    private PrizeType benefitPrizeType;
    private DrawOrderStatus orderStatus;
    private BenefitStatus benefitStatus;
    private Long recordRewardDefinitionId;
    private Long benefitRewardDefinitionId;
    private RewardType recordRewardType;
    private RewardType benefitRewardType;
    private Long recordRewardTargetId;
    private Long benefitRewardTargetId;
    private Long recordRewardQuantity;
    private Long benefitRewardQuantity;
    private String recordRewardPayload;
    private String benefitRewardPayload;
    private String recordRewardFingerprint;
    private String benefitRewardFingerprint;
}
