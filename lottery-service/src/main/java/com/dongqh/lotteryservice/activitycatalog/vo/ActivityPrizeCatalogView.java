package com.dongqh.lotteryservice.activitycatalog.vo;

import com.dongqh.lotteryservice.activityprize.domain.PrizeType;

public record ActivityPrizeCatalogView(Long prizeId, String prizeName, PrizeType prizeType, String imageUrl,
                                       Integer sortOrder, Long hmallItemId) {
}
