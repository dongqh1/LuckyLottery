package com.dongqh.lottery.domain.vo.draworder;

import com.dongqh.lottery.enums.drawtransaction.DrawResultType;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;

public record DrawResultView(long recordId, int sequenceNo, DrawResultType resultType, Long prizeId,
                             String prizeName, PrizeType prizeType, String prizeImageUrl,
                             Long benefitId, Long hmallItemId) {
}
