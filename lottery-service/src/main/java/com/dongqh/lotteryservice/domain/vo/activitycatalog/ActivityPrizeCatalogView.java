package com.dongqh.lotteryservice.domain.vo.activitycatalog;

import com.dongqh.lotteryservice.enums.activityprize.PrizeType;

public record ActivityPrizeCatalogView(Long prizeId, String prizeName, PrizeType prizeType, String imageUrl,
                                       Integer sortOrder, Long hmallItemId) {
}
