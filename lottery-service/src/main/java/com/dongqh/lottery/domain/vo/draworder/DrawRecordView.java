package com.dongqh.lottery.domain.vo.draworder;

import com.dongqh.lottery.enums.drawtransaction.DrawResultType;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;

import java.time.LocalDateTime;

public record DrawRecordView(Long id, Long orderId, String requestId, Integer sequenceNo, Long userId,
                             Long activityId, DrawResultType resultType, Long prizeId, String prizeName,
                             PrizeType prizeType, String prizeImageUrl, Long benefitId, LocalDateTime drawTime) {
}
