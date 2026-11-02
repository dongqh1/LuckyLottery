package com.dongqh.fulfillment.persistence;

import com.dongqh.fulfillment.enums.FailureCategory;
import com.dongqh.fulfillment.enums.FulfillmentStatus;

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
