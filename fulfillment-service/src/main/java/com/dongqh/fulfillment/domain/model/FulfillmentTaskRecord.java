package com.dongqh.fulfillment.domain.model;

import com.dongqh.fulfillment.enums.FulfillmentStatus;
import com.dongqh.fulfillment.enums.FulfillmentType;

import java.time.LocalDateTime;

public record FulfillmentTaskRecord(
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
        LocalDateTime nextAttemptAt
) {

    public FulfillmentTaskRecord withId(Long newId) {
        return new FulfillmentTaskRecord(
                newId,
                fulfillmentNo,
                sourceType,
                sourceId,
                fulfillmentType,
                targetUserId,
                requestPayload,
                requestFingerprint,
                status,
                attemptCount,
                maxAttempts,
                nextAttemptAt
        );
    }
}
