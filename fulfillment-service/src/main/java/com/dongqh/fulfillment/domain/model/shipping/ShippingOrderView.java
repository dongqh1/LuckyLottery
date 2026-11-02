package com.dongqh.fulfillment.domain.model.shipping;

import com.dongqh.fulfillment.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillment.enums.shipping.ShippingStatus;

import java.time.LocalDateTime;

public record ShippingOrderView(
        Long id,
        String shippingNo,
        ShippingSourceType sourceType,
        String sourceId,
        Long targetUserId,
        Long addressSnapshotId,
        String addressSnapshotNo,
        Long hmallItemId,
        String skuCode,
        String productName,
        String imageUrl,
        Integer quantity,
        String fulfillmentNo,
        String carrierCode,
        String carrierName,
        String waybillNo,
        ShippingStatus status,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime failedAt,
        LocalDateTime terminatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String receiverMasked,
        String phoneMasked,
        String regionMasked
) {
}
