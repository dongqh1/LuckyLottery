package com.dongqh.lotteryservice.domain.vo.activityprize;

import com.dongqh.lotteryservice.enums.activityprize.PrizeLevel;
import com.dongqh.lotteryservice.enums.activityprize.PrizeType;

public record ActivityPrizeView(
        Long id, Long activityId, Long prizeId, String prizeName, PrizeType prizeType,
        PrizeLevel prizeLevel, String imageUrl, Integer prizeStatus, Integer weight,
        Integer totalStock, Integer remainingStock, Integer sortOrder
) {
}
