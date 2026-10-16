package com.dongqh.luckyhub.domain.vo.lottery;

import com.dongqh.luckyhub.enums.lottery.DrawResultType;
import com.dongqh.luckyhub.enums.prize.PrizeType;

public record DrawResultView(
        long recordId, int sequenceNo, DrawResultType resultType, Long prizeId,
        String prizeName, PrizeType prizeType, String prizeImageUrl, Long benefitId,
        Long hmallItemId) {

    public DrawResultView(long recordId, int sequenceNo, DrawResultType resultType, Long prizeId,
                          String prizeName, PrizeType prizeType, String prizeImageUrl, Long benefitId) {
        this(recordId, sequenceNo, resultType, prizeId, prizeName, prizeType,
                prizeImageUrl, benefitId, null);
    }
}
