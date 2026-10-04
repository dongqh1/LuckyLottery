package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.enums.FailureCategory;
import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.enums.FulfillmentType;

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
