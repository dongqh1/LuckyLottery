package com.dongqh.luckyhub.domain.vo.activity;

import com.dongqh.luckyhub.enums.prize.PrizeLevel;
import com.dongqh.luckyhub.enums.prize.PrizeType;

public record ActivityPrizeView(
        Long id,
        Long activityId,
        Long prizeId,
        String prizeName,
        PrizeType prizeType,
        PrizeLevel prizeLevel,
        String imageUrl,
        Integer prizeStatus,
        Integer weight,
        Integer totalStock,
        Integer remainingStock,
        Integer sortOrder
) {
}
