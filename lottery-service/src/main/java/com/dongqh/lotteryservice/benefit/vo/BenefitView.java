package com.dongqh.lotteryservice.benefit.vo;

import com.dongqh.lotteryservice.drawtransaction.enums.BenefitStatus;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeType;
import com.dongqh.lotteryservice.prizecatalog.domain.RewardType;

import java.time.LocalDateTime;

public record BenefitView(Long id, Long drawRecordId, Long userId, Long prizeId, PrizeType prizeType,
                          String prizeName, String prizeImageUrl, Integer quantity, BenefitStatus status,
                          LocalDateTime obtainedAt, LocalDateTime expireAt,
                          Long rewardDefinitionId, RewardType rewardType, Long rewardQuantity,
                          String fulfillmentNo, String fulfillmentStatus,
                          String shippingNo, String shippingStatus) {
}
