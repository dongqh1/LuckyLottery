package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.enums.AttemptOperation;
import com.dongqh.fulfillmentservice.enums.FailureCategory;
import com.dongqh.fulfillmentservice.enums.GatewayOutcome;

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
