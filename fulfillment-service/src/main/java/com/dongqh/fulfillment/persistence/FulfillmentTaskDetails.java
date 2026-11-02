package com.dongqh.fulfillment.persistence;

import com.dongqh.fulfillment.enums.FailureCategory;
import com.dongqh.fulfillment.enums.FulfillmentStatus;
import com.dongqh.fulfillment.enums.FulfillmentType;

import java.time.LocalDateTime;

public record FulfillmentTaskDetails(
        Long id,
        String fulfillmentNo,
        String sourceType,
        String sourceId,
        FulfillmentType fulfillmentType,
        Long targetUserId,
        String requestPayload,
        String requestFingerprint,
        FulfillmentStatus status,
        int attemptCount,
        int maxAttempts,
        LocalDateTime nextAttemptAt,
        String externalReference,
        FailureCategory lastErrorCategory,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
