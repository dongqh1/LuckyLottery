package com.dongqh.fulfillment.persistence.shipping;

import com.dongqh.fulfillment.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillment.enums.shipping.ShippingStatus;

import java.time.LocalDateTime;

public record ShippingOrder(
        Long id,
        String shippingNo,
        ShippingSourceType sourceType,
        String sourceId,
        Long targetUserId,
        Long addressSnapshotId,
        Long hmallItemId,
        String skuCode,
        String productName,
        String imageUrl,
        Integer quantity,
        String fulfillmentNo,
        String claimRequestId,
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
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public ShippingOrder withId(Long newId) {
        return new ShippingOrder(newId, shippingNo, sourceType, sourceId, targetUserId, addressSnapshotId,
                hmallItemId, skuCode, productName, imageUrl, quantity, fulfillmentNo, claimRequestId, carrierCode,
                carrierName, waybillNo, status, lastErrorCode, lastErrorMessage, shippedAt, deliveredAt, failedAt,
                terminatedAt, version, createdAt, updatedAt);
    }
}
