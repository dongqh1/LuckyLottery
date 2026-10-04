package com.dongqh.lotteryservice.activityprize.vo;

import com.dongqh.lotteryservice.activityprize.domain.PrizeLevel;
import com.dongqh.lotteryservice.activityprize.domain.PrizeType;

public record ActivityPrizeView(
        Long id, Long activityId, Long prizeId, String prizeName, PrizeType prizeType,
        PrizeLevel prizeLevel, String imageUrl, Integer prizeStatus, Integer weight,
        Integer totalStock, Integer remainingStock, Integer sortOrder
) {
}
