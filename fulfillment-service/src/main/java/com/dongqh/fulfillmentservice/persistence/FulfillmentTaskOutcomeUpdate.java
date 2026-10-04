package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.enums.FailureCategory;
import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;

import java.time.LocalDateTime;

public record FulfillmentTaskOutcomeUpdate(
        long taskId,
        String leaseToken,
        FulfillmentStatus status,
        int attemptCount,
        LocalDateTime nextAttemptAt,
        String externalReference,
        FailureCategory errorCategory,
        String errorCode,
        String errorMessage,
        LocalDateTime completedAt
) {
}
