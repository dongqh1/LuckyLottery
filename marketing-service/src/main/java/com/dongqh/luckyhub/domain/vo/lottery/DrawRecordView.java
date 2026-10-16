package com.dongqh.luckyhub.domain.vo.lottery;

import com.dongqh.luckyhub.enums.lottery.DrawResultType;
import com.dongqh.luckyhub.enums.prize.PrizeType;
import java.time.LocalDateTime;

public record DrawRecordView(Long id, Long orderId, String requestId, Integer sequenceNo, Long userId,
                             Long activityId, DrawResultType resultType, Long prizeId, String prizeName,
                             PrizeType prizeType, String prizeImageUrl, Long benefitId, LocalDateTime drawTime) {
}
