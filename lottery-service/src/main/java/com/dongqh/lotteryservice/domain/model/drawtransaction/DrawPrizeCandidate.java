package com.dongqh.lotteryservice.domain.model.drawtransaction;

import com.dongqh.lotteryservice.algorithm.WeightedPrize;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;

import java.util.Objects;

public record DrawPrizeCandidate(long activityPrizeId, long prizeId, String prizeName, PrizeType prizeType,
                                String prizeImageUrl, long weight, int remainingStock, boolean enabled,
                                Long rewardDefinitionId, RewardType rewardType,
                                Long rewardTargetId, Long rewardQuantity) {

    public DrawPrizeCandidate {
        if (activityPrizeId <= 0 || prizeId <= 0) {
            throw new IllegalArgumentException("prize identifiers must be positive");
        }
        if (prizeName == null || prizeName.isBlank()) {
            throw new IllegalArgumentException("prizeName must not be blank");
        }
        Objects.requireNonNull(prizeType, "prizeType must not be null");
        if (weight <= 0 || remainingStock < 0) {
            throw new IllegalArgumentException("weight must be positive and stock non-negative");
        }
    }

    public WeightedPrize toWeightedPrize() {
        return new WeightedPrize(activityPrizeId, prizeId, weight, remainingStock, enabled);
    }
}
