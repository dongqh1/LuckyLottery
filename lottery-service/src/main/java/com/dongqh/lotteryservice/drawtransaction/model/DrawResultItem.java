package com.dongqh.lotteryservice.drawtransaction.model;

import com.dongqh.lotteryservice.drawtransaction.enums.DrawResultType;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeType;

public record DrawResultItem(long recordId, int sequenceNo, DrawResultType resultType, Long prizeId,
                             String prizeName, PrizeType prizeType, String prizeImageUrl, Long benefitId,
                             RewardSnapshot rewardSnapshot) {
    public DrawResultItem(long recordId, int sequenceNo, DrawResultType resultType, Long prizeId,
                          String prizeName, PrizeType prizeType, String prizeImageUrl, Long benefitId) {
        this(recordId, sequenceNo, resultType, prizeId, prizeName, prizeType, prizeImageUrl, benefitId, null);
    }
}
