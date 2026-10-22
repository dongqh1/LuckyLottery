package com.dongqh.lotteryservice.algorithm;

public record WeightedPrize(
        long activityPrizeId,
        long prizeId,
        long weight,
        int remainingStock,
        boolean enabled
) {
}
