package com.dongqh.fulfillment.persistence;

import com.dongqh.fulfillment.enums.FailureCategory;

import java.time.LocalDateTime;

public record FulfillmentQuarantineRecord(
        long taskId,
        String fulfillmentNo,
        String reason,
        FailureCategory errorCategory,
        String errorCode,
        String errorMessage,
        LocalDateTime quarantinedAt
) {
}
