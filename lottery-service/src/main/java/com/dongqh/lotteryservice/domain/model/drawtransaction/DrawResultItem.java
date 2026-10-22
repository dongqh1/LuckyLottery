package com.dongqh.lotteryservice.domain.model.drawtransaction;

import com.dongqh.lotteryservice.enums.drawtransaction.DrawResultType;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;

public record DrawResultItem(long recordId, int sequenceNo, DrawResultType resultType, Long prizeId,
                             String prizeName, PrizeType prizeType, String prizeImageUrl, Long benefitId,
                             Long itemId) {
}
