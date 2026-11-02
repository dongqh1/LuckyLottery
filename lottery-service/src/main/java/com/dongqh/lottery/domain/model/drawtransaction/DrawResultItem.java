package com.dongqh.lottery.domain.model.drawtransaction;

import com.dongqh.lottery.enums.drawtransaction.DrawResultType;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;

public record DrawResultItem(long recordId, int sequenceNo, DrawResultType resultType, Long prizeId,
                             String prizeName, PrizeType prizeType, String prizeImageUrl, Long benefitId,
                             Long itemId) {
}
