package com.dongqh.fulfillment.domain.model;

import com.dongqh.fulfillment.enums.FailureCategory;
import com.dongqh.fulfillment.enums.FulfillmentStatus;
import com.dongqh.fulfillment.enums.FulfillmentType;

import java.time.LocalDateTime;

public record FulfillmentTaskView(
        Long id,
        String fulfillmentNo,
        String sourceType,
        String sourceId,
        FulfillmentType fulfillmentType,
        Long targetUserId,
        FulfillmentPayload payload,
        String requestFingerprint,
        FulfillmentStatus status,
        Integer attemptCount,
        Integer maxAttempts,
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
