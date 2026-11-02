package com.dongqh.fulfillment.persistence;

import com.dongqh.fulfillment.enums.FulfillmentStatus;

public record FulfillmentTaskClaimSnapshot(
        FulfillmentStatus status,
        String leaseToken,
        int attemptCount,
        int maxAttempts
) {
}
