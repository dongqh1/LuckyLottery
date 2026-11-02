package com.dongqh.lottery.domain.vo.activitycatalog;

import com.dongqh.lottery.enums.activityprize.PrizeType;

public record ActivityPrizeCatalogView(Long prizeId, String prizeName, PrizeType prizeType, String imageUrl,
                                       Integer sortOrder, Long hmallItemId) {
}
