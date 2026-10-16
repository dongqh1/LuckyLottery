package com.dongqh.fulfillmentservice.domain.model;

import com.dongqh.fulfillmentservice.enums.FailureCategory;
import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.enums.FulfillmentType;

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
