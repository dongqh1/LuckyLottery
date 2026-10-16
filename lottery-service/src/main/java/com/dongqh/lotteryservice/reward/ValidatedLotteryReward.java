package com.dongqh.lotteryservice.reward;

import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.domain.model.drawtransaction.RewardSnapshot;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;

public record ValidatedLotteryReward(long benefitId, long drawRecordId, long orderId,
        String requestId, long userId, long activityId, long prizeId, PrizeType prizeType,
        BenefitStatus benefitStatus, RewardSnapshot rewardSnapshot) {
}
