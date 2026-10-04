package com.dongqh.fulfillmentservice.domain;

import com.dongqh.fulfillmentservice.enums.FailureCategory;
import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;

import java.time.LocalDateTime;

public record FulfillmentTransition(
        FulfillmentStatus status,
        LocalDateTime nextAttemptAt,
        LocalDateTime completedAt,
        FailureCategory failureCategory
) {
}
