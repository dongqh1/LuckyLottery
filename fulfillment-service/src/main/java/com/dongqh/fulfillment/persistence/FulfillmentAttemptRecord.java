package com.dongqh.fulfillment.persistence;

import com.dongqh.fulfillment.enums.AttemptOperation;
import com.dongqh.fulfillment.enums.FailureCategory;
import com.dongqh.fulfillment.enums.GatewayOutcome;

import java.time.LocalDateTime;

public record FulfillmentAttemptRecord(
        long taskId,
        String fulfillmentNo,
        int sequenceNo,
        AttemptOperation operation,
        GatewayOutcome outcome,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long durationMs,
        String externalReference,
        FailureCategory errorCategory,
        String errorCode,
        String errorMessage
) {
}
