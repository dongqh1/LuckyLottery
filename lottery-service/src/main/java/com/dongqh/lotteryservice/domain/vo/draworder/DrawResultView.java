package com.dongqh.lotteryservice.domain.vo.draworder;

import com.dongqh.lotteryservice.enums.drawtransaction.DrawResultType;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;

public record DrawResultView(long recordId, int sequenceNo, DrawResultType resultType, Long prizeId,
                             String prizeName, PrizeType prizeType, String prizeImageUrl,
                             Long benefitId, Long hmallItemId) {
}
