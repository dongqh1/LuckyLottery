package com.dongqh.fulfillment.domain;

import com.dongqh.fulfillment.enums.FailureCategory;
import com.dongqh.fulfillment.enums.FulfillmentStatus;

import java.time.LocalDateTime;

public record FulfillmentTransition(
        FulfillmentStatus status,
        LocalDateTime nextAttemptAt,
        LocalDateTime completedAt,
        FailureCategory failureCategory
) {
}
