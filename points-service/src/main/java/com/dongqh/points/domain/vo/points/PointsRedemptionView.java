package com.dongqh.points.domain.vo.points;

import com.dongqh.points.enums.points.PointsProductType;
import com.dongqh.points.enums.points.PointsRedemptionStatus;

import java.time.LocalDateTime;

public record PointsRedemptionView(
        Long id,
        String redemptionNo,
        Long userId,
        Long skuId,
        Long offerId,
        Long hmallItemId,
        Integer quantity,
        Long unitPoints,
        Long totalPoints,
        Integer cashPriceCent,
        String productCode,
        String productName,
        String skuCode,
        String skuName,
        PointsProductType productType,
        String imageUrl,
        PointsRedemptionStatus status,
        String reversalNo,
        String failureReason,
        PointsShippingAddressView addressSnapshot,
        Long shippingOrderId,
        String shippingNo,
        String shippingStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
