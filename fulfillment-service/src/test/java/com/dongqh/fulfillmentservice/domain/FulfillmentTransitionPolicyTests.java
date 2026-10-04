package com.dongqh.fulfillmentservice.domain;

import com.dongqh.fulfillmentservice.enums.AttemptOperation;
import com.dongqh.fulfillmentservice.enums.FailureCategory;
import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.enums.GatewayOutcome;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentTransitionPolicyTests {

    private final FulfillmentTransitionPolicy policy = new FulfillmentTransitionPolicy();
    private final LocalDateTime now = LocalDateTime.parse("2026-09-27T10:00:00.123");

    @Test
    void mapsTerminalAndUnknownGatewayOutcomes() {
        assertTransition(GatewayOutcome.SUCCEEDED, AttemptOperation.EXECUTE, 1, 5,
                FulfillmentStatus.SUCCEEDED, null, now);
        assertTransition(GatewayOutcome.UNKNOWN, AttemptOperation.EXECUTE, 1, 5,
                FulfillmentStatus.RECONCILING, now, null);
        assertTransition(GatewayOutcome.PERMANENT_FAILURE, AttemptOperation.EXECUTE, 1, 5,
                FulfillmentStatus.QUARANTINED, null, null);
    }

    @Test
    void retriesWithinLimitWithCappedExponentialBackoff() {
        FulfillmentTransition firstRetry = policy.resolve(
                GatewayOutcome.RETRYABLE_FAILURE, AttemptOperation.EXECUTE, 1, 5,
                now, Duration.ofSeconds(2), Duration.ofSeconds(5));
        FulfillmentTransition cappedRetry = policy.resolve(
                GatewayOutcome.RETRYABLE_FAILURE, AttemptOperation.EXECUTE, 8, 10,
                now, Duration.ofSeconds(2), Duration.ofSeconds(5));

        assertThat(firstRetry.status()).isEqualTo(FulfillmentStatus.RETRY_WAITING);
        assertThat(firstRetry.nextAttemptAt()).isEqualTo(now.plusSeconds(2));
        assertThat(firstRetry.failureCategory()).isEqualTo(FailureCategory.RETRYABLE);
        assertThat(cappedRetry.nextAttemptAt()).isEqualTo(now.plusSeconds(5));
    }

    @Test
    void quarantinesWhenRetryLimitIsReachedAndTreatsNotFoundByOperation() {
        FulfillmentTransition exhausted = policy.resolve(
                GatewayOutcome.RETRYABLE_FAILURE, AttemptOperation.EXECUTE, 5, 5,
                now, Duration.ofSeconds(1), Duration.ofSeconds(30));
        FulfillmentTransition missingQuery = policy.resolve(
                GatewayOutcome.NOT_FOUND, AttemptOperation.QUERY, 5, 5,
                now, Duration.ofSeconds(1), Duration.ofSeconds(30));
        FulfillmentTransition missingExecution = policy.resolve(
                GatewayOutcome.NOT_FOUND, AttemptOperation.EXECUTE, 2, 5,
                now, Duration.ofSeconds(1), Duration.ofSeconds(30));

        assertThat(exhausted.status()).isEqualTo(FulfillmentStatus.QUARANTINED);
        assertThat(missingQuery.status()).isEqualTo(FulfillmentStatus.PENDING);
        assertThat(missingQuery.nextAttemptAt()).isEqualTo(now);
        assertThat(missingExecution.status()).isEqualTo(FulfillmentStatus.RETRY_WAITING);
        assertThat(missingExecution.nextAttemptAt()).isEqualTo(now.plusSeconds(2));
    }

    private void assertTransition(
            GatewayOutcome outcome,
            AttemptOperation operation,
            int attempts,
            int maxAttempts,
            FulfillmentStatus expectedStatus,
            LocalDateTime expectedNextAttempt,
            LocalDateTime expectedCompletedAt
    ) {
        FulfillmentTransition transition = policy.resolve(
                outcome, operation, attempts, maxAttempts,
                now, Duration.ofSeconds(1), Duration.ofSeconds(10));

        assertThat(transition.status()).isEqualTo(expectedStatus);
        assertThat(transition.nextAttemptAt()).isEqualTo(expectedNextAttempt);
        assertThat(transition.completedAt()).isEqualTo(expectedCompletedAt);
    }
}
