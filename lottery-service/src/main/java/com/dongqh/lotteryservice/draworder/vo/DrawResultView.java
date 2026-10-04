package com.dongqh.lotteryservice.draworder.vo;

import com.dongqh.lotteryservice.drawtransaction.enums.DrawResultType;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeType;

public record DrawResultView(long recordId, int sequenceNo, DrawResultType resultType, Long prizeId,
                             String prizeName, PrizeType prizeType, String prizeImageUrl,
                             Long benefitId, Long hmallItemId) {
}
