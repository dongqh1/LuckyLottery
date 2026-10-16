package com.dongqh.luckyhub.domain.algorithm.lottery;

public record PrizeWeightSnapshot(
        long activityPrizeId,
        long prizeId,
        long weight,
        int remainingStock,
        boolean enabled
) {
}
