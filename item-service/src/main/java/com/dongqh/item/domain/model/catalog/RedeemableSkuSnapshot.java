package com.dongqh.item.domain.model.catalog;

import com.dongqh.item.enums.catalog.ProductType;

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
