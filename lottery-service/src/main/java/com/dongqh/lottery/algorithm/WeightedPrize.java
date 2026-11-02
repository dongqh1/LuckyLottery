package com.dongqh.lottery.algorithm;

public record WeightedPrize(
        long activityPrizeId,
        long prizeId,
        long weight,
        int remainingStock,
        boolean enabled
) {
}
