package com.dongqh.hmapi.dto;

import java.time.LocalDateTime;

public record FulfillmentShippingOrderDTO(
        Long id,
        String shippingNo,
        String fulfillmentNo,
        String status,
        Long addressSnapshotId,
        String addressSnapshotNo,
        String carrierCode,
        String carrierName,
        String waybillNo,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime failedAt,
        LocalDateTime terminatedAt,
        String receiverMasked,
        String phoneMasked,
        String regionMasked
) {
}
