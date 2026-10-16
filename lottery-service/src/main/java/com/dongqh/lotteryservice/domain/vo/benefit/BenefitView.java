package com.dongqh.lotteryservice.domain.vo.benefit;

import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;

import java.time.LocalDateTime;

public record BenefitView(Long id, Long drawRecordId, Long userId, Long prizeId, PrizeType prizeType,
                          String prizeName, String prizeImageUrl, Integer quantity, BenefitStatus status,
                          LocalDateTime obtainedAt, LocalDateTime expireAt,
                          Long rewardDefinitionId, RewardType rewardType, Long rewardQuantity,
                          String fulfillmentNo, String fulfillmentStatus,
                          String shippingNo, String shippingStatus) {
}
