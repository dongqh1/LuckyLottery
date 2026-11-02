package com.dongqh.lottery.domain.vo.benefit;

import com.dongqh.lottery.enums.drawtransaction.BenefitStatus;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;
import com.dongqh.lottery.enums.prizecatalog.RewardType;

import java.time.LocalDateTime;

public record BenefitView(Long id, Long drawRecordId, Long userId, Long prizeId, PrizeType prizeType,
                          String prizeName, String prizeImageUrl, Integer quantity, BenefitStatus status,
                          LocalDateTime obtainedAt, LocalDateTime expireAt,
                          Long rewardDefinitionId, RewardType rewardType, Long rewardQuantity,
                          String shippingNo, String shippingStatus) {
}
