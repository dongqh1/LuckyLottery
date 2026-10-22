package com.dongqh.lotteryservice.reward;

import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;

public record ValidatedLotteryReward(long benefitId, long drawRecordId, long orderId,
        String requestId, long userId, long activityId, long prizeId, BenefitStatus benefitStatus) {
}
