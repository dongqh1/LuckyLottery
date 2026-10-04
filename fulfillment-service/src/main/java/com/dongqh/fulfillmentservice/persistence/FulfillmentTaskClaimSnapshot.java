package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;

public record FulfillmentTaskClaimSnapshot(
        FulfillmentStatus status,
        String leaseToken,
        int attemptCount,
        int maxAttempts
) {
}
