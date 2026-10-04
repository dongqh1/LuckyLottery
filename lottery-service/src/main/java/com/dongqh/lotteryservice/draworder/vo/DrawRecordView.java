package com.dongqh.lotteryservice.draworder.vo;

import com.dongqh.lotteryservice.drawtransaction.enums.DrawResultType;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeType;

import java.time.LocalDateTime;

public record DrawRecordView(Long id, Long orderId, String requestId, Integer sequenceNo, Long userId,
                             Long activityId, DrawResultType resultType, Long prizeId, String prizeName,
                             PrizeType prizeType, String prizeImageUrl, Long benefitId, LocalDateTime drawTime) {
}
