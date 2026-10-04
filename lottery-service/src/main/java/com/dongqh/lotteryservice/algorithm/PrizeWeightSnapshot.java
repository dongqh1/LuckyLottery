package com.dongqh.lotteryservice.algorithm;

public record PrizeWeightSnapshot(
        long activityPrizeId,
        long prizeId,
        long weight,
        int remainingStock,
        boolean enabled
) {
}
