package com.dongqh.luckyhub.domain.vo.benefit;

import com.dongqh.luckyhub.enums.benefit.BenefitStatus;
import com.dongqh.luckyhub.enums.prize.PrizeType;
import com.dongqh.luckyhub.enums.reward.RewardType;
import com.dongqh.luckyhub.enums.fulfillment.FulfillmentStatus;
import com.dongqh.luckyhub.enums.shipping.ShippingStatus;
import java.time.LocalDateTime;

public record BenefitView(Long id, Long drawRecordId, Long userId, Long prizeId, PrizeType prizeType,
                          String prizeName, String prizeImageUrl, Integer quantity, BenefitStatus status,
                          LocalDateTime obtainedAt, LocalDateTime expireAt,
                          Long rewardDefinitionId, RewardType rewardType, Long rewardQuantity,
                          String fulfillmentNo, FulfillmentStatus fulfillmentStatus,
                          String shippingNo, ShippingStatus shippingStatus) {
    public BenefitView(Long id, Long drawRecordId, Long userId, Long prizeId, PrizeType prizeType,
                       String prizeName, String prizeImageUrl, Integer quantity, BenefitStatus status,
                       LocalDateTime obtainedAt, LocalDateTime expireAt, Long rewardDefinitionId,
                       RewardType rewardType, Long rewardQuantity, String fulfillmentNo,
                       FulfillmentStatus fulfillmentStatus) {
        this(id, drawRecordId, userId, prizeId, prizeType, prizeName, prizeImageUrl, quantity, status,
                obtainedAt, expireAt, rewardDefinitionId, rewardType, rewardQuantity, fulfillmentNo,
                fulfillmentStatus, null, null);
    }
}
