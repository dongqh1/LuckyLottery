package com.dongqh.lotteryservice.domain.model.drawtransaction;

import com.dongqh.lotteryservice.enums.drawtransaction.DrawResultType;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;

public record DrawResultItem(long recordId, int sequenceNo, DrawResultType resultType, Long prizeId,
                             String prizeName, PrizeType prizeType, String prizeImageUrl, Long benefitId,
                             RewardSnapshot rewardSnapshot) {
    public DrawResultItem(long recordId, int sequenceNo, DrawResultType resultType, Long prizeId,
                          String prizeName, PrizeType prizeType, String prizeImageUrl, Long benefitId) {
        this(recordId, sequenceNo, resultType, prizeId, prizeName, prizeType, prizeImageUrl, benefitId, null);
    }
}
