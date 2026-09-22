package com.dongqh.luckyhub.points.vo;

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
