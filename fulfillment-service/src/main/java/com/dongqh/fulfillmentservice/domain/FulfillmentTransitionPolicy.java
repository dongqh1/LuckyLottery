package com.dongqh.fulfillmentservice.domain;

import com.dongqh.fulfillmentservice.enums.AttemptOperation;
import com.dongqh.fulfillmentservice.enums.FailureCategory;
import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.enums.GatewayOutcome;

import java.time.Duration;
import java.time.LocalDateTime;

public class FulfillmentTransitionPolicy {

    public FulfillmentTransition resolve(
            GatewayOutcome outcome,
            AttemptOperation operation,
            int executionAttempts,
            int maxAttempts,
            LocalDateTime now,
            Duration baseDelay,
            Duration maxDelay
    ) {
        FailureCategory failureCategory = category(outcome);
        return switch (outcome) {
            case SUCCEEDED -> new FulfillmentTransition(FulfillmentStatus.SUCCEEDED, null, now, null);
            case UNKNOWN -> new FulfillmentTransition(
                    FulfillmentStatus.RECONCILING, now, null, FailureCategory.UNKNOWN);
            case RETRYABLE_FAILURE -> executionAttempts >= maxAttempts
                    ? new FulfillmentTransition(
                            FulfillmentStatus.QUARANTINED, null, null, failureCategory)
                    : new FulfillmentTransition(
                            FulfillmentStatus.RETRY_WAITING,
                            now.plus(backoff(executionAttempts, baseDelay, maxDelay)),
                            null,
                            failureCategory);
            case PERMANENT_FAILURE -> new FulfillmentTransition(
                    FulfillmentStatus.QUARANTINED, null, null, failureCategory);
            case NOT_FOUND -> operation == AttemptOperation.QUERY
                    ? new FulfillmentTransition(FulfillmentStatus.PENDING, now, null, null)
                    : new FulfillmentTransition(
                            FulfillmentStatus.RETRY_WAITING,
                            now.plus(backoff(executionAttempts, baseDelay, maxDelay)),
                            null,
                            null);
        };
    }

    private Duration backoff(int attempts, Duration baseDelay, Duration maxDelay) {
        long multiplier = 1L << Math.min(30, Math.max(0, attempts - 1));
        Duration calculated;
        try {
            calculated = baseDelay.multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            calculated = maxDelay;
        }
        return calculated.compareTo(maxDelay) > 0 ? maxDelay : calculated;
    }

    private FailureCategory category(GatewayOutcome outcome) {
        return switch (outcome) {
            case RETRYABLE_FAILURE -> FailureCategory.RETRYABLE;
            case PERMANENT_FAILURE -> FailureCategory.PERMANENT;
            case UNKNOWN -> FailureCategory.UNKNOWN;
            default -> null;
        };
    }
}
