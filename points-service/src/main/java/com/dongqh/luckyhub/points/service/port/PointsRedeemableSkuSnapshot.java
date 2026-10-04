package com.dongqh.luckyhub.points.service.port;

public record PointsRedeemableSkuSnapshot(
        Long skuId,
        String productCode,
        String productName,
        String skuCode,
        String skuName,
        String productType,
        String imageUrl,
        Long pointsPrice
) {
}
