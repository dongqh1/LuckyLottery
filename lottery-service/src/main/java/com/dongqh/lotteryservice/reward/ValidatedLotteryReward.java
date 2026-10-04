package com.dongqh.lotteryservice.reward;

import com.dongqh.lotteryservice.drawtransaction.enums.BenefitStatus;
import com.dongqh.lotteryservice.drawtransaction.model.RewardSnapshot;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeType;

public record ValidatedLotteryReward(long benefitId, long drawRecordId, long orderId,
        String requestId, long userId, long activityId, long prizeId, PrizeType prizeType,
        BenefitStatus benefitStatus, RewardSnapshot rewardSnapshot) {
}
