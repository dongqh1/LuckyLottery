package com.dongqh.luckyhub.domain.model.lottery;

import com.dongqh.luckyhub.enums.lottery.DrawResultType;
import com.dongqh.luckyhub.enums.prize.PrizeType;
import com.dongqh.luckyhub.domain.model.reward.RewardSnapshot;

public record DrawResultItem(
        long recordId,
        int sequenceNo,
        DrawResultType resultType,
        Long prizeId,
        String prizeName,
        PrizeType prizeType,
        String prizeImageUrl,
        Long benefitId,
        RewardSnapshot rewardSnapshot) {

    public DrawResultItem(long recordId, int sequenceNo, DrawResultType resultType,
                          Long prizeId, String prizeName, PrizeType prizeType,
                          String prizeImageUrl, Long benefitId) {
        this(recordId, sequenceNo, resultType, prizeId, prizeName, prizeType,
                prizeImageUrl, benefitId, null);
    }
}
