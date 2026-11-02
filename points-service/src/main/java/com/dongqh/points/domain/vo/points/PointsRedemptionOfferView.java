package com.dongqh.points.domain.vo.points;

public record PointsRedemptionOfferView(
        Long id,
        Long hmallItemId,
        Long unitPoints,
        Integer availableStock,
        String itemName,
        String imageUrl,
        Integer cashPriceCent
) {
}
