package com.dongqh.luckyhub.domain.model.catalog;

import com.dongqh.luckyhub.enums.catalog.ProductType;

public record RedeemableSkuSnapshot(
        Long skuId,
        String productCode,
        String productName,
        String skuCode,
        String skuName,
        ProductType productType,
        String imageUrl,
        Long pointsPrice
) {
}
