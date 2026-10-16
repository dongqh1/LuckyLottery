package com.dongqh.luckyhub.domain.vo.catalog;

import java.time.LocalDateTime;

public record SkuView(
        Long id,
        Long productId,
        String skuCode,
        String skuName,
        Long cashPriceCent,
        Long pointsPrice,
        Boolean cashEnabled,
        Boolean pointsEnabled,
        Integer status,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
