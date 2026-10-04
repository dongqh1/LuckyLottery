package com.dongqh.hmapi.dto;

public record RedeemableSkuDTO(
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
