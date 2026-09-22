package com.dongqh.luckyhub.lottery.vo;

import com.dongqh.luckyhub.prize.enums.PrizeType;

/** 用户端可见奖池项，不暴露权重与库存。 */
public record LotteryPrizeView(
        Long prizeId,
        String prizeName,
        PrizeType prizeType,
        String imageUrl,
        Integer sortOrder,
        Long hmallItemId
) {
    public LotteryPrizeView(Long prizeId, String prizeName, PrizeType prizeType,
                            String imageUrl, Integer sortOrder) {
        this(prizeId, prizeName, prizeType, imageUrl, sortOrder, null);
    }
}
