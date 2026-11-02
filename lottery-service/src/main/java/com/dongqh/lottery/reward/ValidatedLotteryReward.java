package com.dongqh.lottery.reward;

import com.dongqh.lottery.enums.drawtransaction.BenefitStatus;

public record ValidatedLotteryReward(long benefitId, long drawRecordId, long orderId,
        String requestId, long userId, long activityId, long prizeId, BenefitStatus benefitStatus) {
}
