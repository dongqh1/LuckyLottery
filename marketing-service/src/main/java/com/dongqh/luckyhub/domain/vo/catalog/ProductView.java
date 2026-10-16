package com.dongqh.luckyhub.domain.vo.catalog;

import com.dongqh.luckyhub.enums.catalog.ProductType;

import java.time.LocalDateTime;
import java.util.List;

public record ProductView(
        Long id,
        String productCode,
        String productName,
        ProductType productType,
        String imageUrl,
        String description,
        Integer status,
        List<SkuView> skus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public ProductView {
        skus = List.copyOf(skus);
    }
}
