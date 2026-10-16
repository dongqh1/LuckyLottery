package com.dongqh.luckyhub.domain.model.lottery;

import com.dongqh.luckyhub.enums.benefit.BenefitStatus;
import com.dongqh.luckyhub.enums.prize.PrizeType;
import com.dongqh.luckyhub.domain.model.reward.RewardSnapshot;

public record ValidatedLotteryReward(long benefitId, long drawRecordId, long orderId,
        String requestId, long userId, long activityId, long prizeId, PrizeType prizeType,
        BenefitStatus benefitStatus, RewardSnapshot rewardSnapshot) {
}
