package com.dongqh.fulfillment.service;

import com.dongqh.fulfillment.enums.AttemptOperation;
import com.dongqh.fulfillment.enums.GatewayOutcome;
import com.dongqh.fulfillment.enums.FulfillmentStatus;
import com.dongqh.fulfillment.enums.FulfillmentType;
import com.dongqh.fulfillment.domain.model.FulfillmentClaim;
import com.dongqh.fulfillment.domain.model.FulfillmentGatewayResult;
import com.dongqh.fulfillment.domain.model.FulfillmentTaskRecord;
import com.dongqh.fulfillment.persistence.FulfillmentAttemptRecord;
import com.dongqh.fulfillment.persistence.FulfillmentQuarantineRecord;
import com.dongqh.fulfillment.persistence.FulfillmentTaskClaimSnapshot;
import com.dongqh.fulfillment.persistence.FulfillmentTaskOutcomeUpdate;
import com.dongqh.fulfillment.persistence.FulfillmentTaskStateRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FulfillmentTaskStateServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 27, 4, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T04:00:00Z"), ZoneOffset.UTC);

    @Test
    void claimReadyProducesExecuteClaimWithLease() {
        FakeRepository repository = new FakeRepository();
        repository.readyTask = task(1, "TASK-1", FulfillmentStatus.PENDING);

        Optional<FulfillmentClaim> claim = service(repository).claimReady(" TASK-1 ", Duration.ofSeconds(30));

        assertThat(claim).isPresent();
        assertThat(claim.orElseThrow().operation()).isEqualTo(AttemptOperation.EXECUTE);
        assertThat(claim.orElseThrow().leaseToken()).isNotBlank();
        assertThat(claim.orElseThrow().claimedAt()).isEqualTo(NOW);
        assertThat(claim.orElseThrow().leaseUntil()).isEqualTo(NOW.plusSeconds(30));
        assertThat(repository.claimedStatuses).containsExactly(FulfillmentStatus.PENDING);
    }

    @Test
    void batchClaimUsesQueryForReconciliationAndSkipsLostClaims() {
        FakeRepository repository = new FakeRepository();
        repository.dueTasks = List.of(
                task(1, "TASK-1", FulfillmentStatus.RETRY_WAITING),
                task(2, "TASK-2", FulfillmentStatus.RECONCILING));
        repository.claimFailures.add(1L);

        List<FulfillmentClaim> claims = service(repository).claimDue(10, Duration.ofMinutes(1));

        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).fulfillmentNo()).isEqualTo("TASK-2");
        assertThat(claims.get(0).operation()).isEqualTo(AttemptOperation.QUERY);
        assertThat(repository.lastLimit).isEqualTo(10);
        assertThat(repository.claimedStatuses)
                .containsExactly(FulfillmentStatus.RETRY_WAITING, FulfillmentStatus.RECONCILING);
    }

    @Test
    void compensationClaimIncludesPendingGraceCutoffAndOnlyReturnsSuccessfulClaims() {
        FakeRepository repository = new FakeRepository();
        repository.compensationTasks = List.of(task(3, "TASK-3", FulfillmentStatus.PENDING));

        List<FulfillmentClaim> claims = service(repository)
                .claimCompensationDue(5, Duration.ofSeconds(20), Duration.ofMinutes(5));

        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).operation()).isEqualTo(AttemptOperation.EXECUTE);
        assertThat(repository.compensationNow).isEqualTo(NOW);
        assertThat(repository.pendingBefore).isEqualTo(NOW.minusMinutes(5));
        assertThat(repository.lastLimit).isEqualTo(5);
    }

    @Test
    void rejectsInvalidClaimArguments() {
        FulfillmentTaskStateService service = service(new FakeRepository());

        assertThatThrownBy(() -> service.claimReady("  ", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.claimDue(0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.claimDue(1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.claimCompensationDue(1, Duration.ofSeconds(1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void successfulExecutionRecordsAttemptAndCompletesTask() {
        FakeRepository repository = activeTask(0, 5, "lease-1");

        service(repository).recordResult(
                claim(AttemptOperation.EXECUTE, "lease-1"), FulfillmentGatewayResult.succeeded("EXT-1"), 12);

        assertThat(repository.attempts).singleElement().satisfies(attempt -> {
            assertThat(attempt.sequenceNo()).isEqualTo(1);
            assertThat(attempt.operation()).isEqualTo(AttemptOperation.EXECUTE);
            assertThat(attempt.outcome()).isEqualTo(GatewayOutcome.SUCCEEDED);
            assertThat(attempt.startedAt()).isEqualTo(NOW);
            assertThat(attempt.finishedAt()).isEqualTo(NOW);
            assertThat(attempt.durationMs()).isEqualTo(12);
        });
        assertThat(repository.outcomeUpdates).singleElement().satisfies(update -> {
            assertThat(update.status()).isEqualTo(FulfillmentStatus.SUCCEEDED);
            assertThat(update.attemptCount()).isEqualTo(1);
            assertThat(update.externalReference()).isEqualTo("EXT-1");
            assertThat(update.completedAt()).isEqualTo(NOW);
        });
        assertThat(repository.quarantines).isEmpty();
    }

    @Test
    void unknownQueryResultReconcilesWithoutCountingAnExecutionAttempt() {
        FakeRepository repository = activeTask(3, 5, "lease-2");

        service(repository).recordResult(
                claim(AttemptOperation.QUERY, "lease-2"), FulfillmentGatewayResult.unknown("TIMEOUT", "unknown"), 9);

        assertThat(repository.outcomeUpdates.get(0).status()).isEqualTo(FulfillmentStatus.RECONCILING);
        assertThat(repository.outcomeUpdates.get(0).attemptCount()).isEqualTo(3);
        assertThat(repository.outcomeUpdates.get(0).nextAttemptAt()).isEqualTo(NOW);
        assertThat(repository.outcomeUpdates.get(0).errorCategory().name()).isEqualTo("UNKNOWN");
    }

    @Test
    void retryableFailureAtAttemptLimitQuarantinesTaskAndUpsertsQuarantine() {
        FakeRepository repository = activeTask(2, 3, "lease-3");

        service(repository).recordResult(
                claim(AttemptOperation.EXECUTE, "lease-3"),
                FulfillmentGatewayResult.retryable("TEMP", "try later"),
                25
        );

        assertThat(repository.outcomeUpdates.get(0).status()).isEqualTo(FulfillmentStatus.QUARANTINED);
        assertThat(repository.outcomeUpdates.get(0).attemptCount()).isEqualTo(3);
        assertThat(repository.quarantines).singleElement().satisfies(quarantine -> {
            assertThat(quarantine.fulfillmentNo()).isEqualTo("TASK-9");
            assertThat(quarantine.reason()).isEqualTo("履约失败");
            assertThat(quarantine.errorCode()).isEqualTo("TEMP");
            assertThat(quarantine.quarantinedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void rejectsResultFromExpiredOrDifferentLeaseWithoutPersistingAttempt() {
        FakeRepository repository = activeTask(0, 5, "new-lease");

        assertThatThrownBy(() -> service(repository).recordResult(
                claim(AttemptOperation.EXECUTE, "old-lease"), FulfillmentGatewayResult.succeeded("EXT-2"), 1))
                .isInstanceOf(FulfillmentLeaseConflictException.class);

        assertThat(repository.attempts).isEmpty();
        assertThat(repository.outcomeUpdates).isEmpty();
    }

    @Test
    void recoversExpiredLeaseBatchAndRejectsInvalidLimit() {
        FakeRepository repository = new FakeRepository();
        repository.recoveredCount = 4;

        assertThat(service(repository).recoverExpiredLeases(20)).isEqualTo(4);
        assertThat(repository.lastLimit).isEqualTo(20);
        assertThatThrownBy(() -> service(repository).recoverExpiredLeases(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static FulfillmentTaskStateService service(FakeRepository repository) {
        return new FulfillmentTaskStateService(
                repository, CLOCK, Duration.ofSeconds(1), Duration.ofMinutes(1));
    }

    private static FakeRepository activeTask(int attempts, int maxAttempts, String leaseToken) {
        FakeRepository repository = new FakeRepository();
        repository.claimSnapshot = new FulfillmentTaskClaimSnapshot(
                FulfillmentStatus.PROCESSING, leaseToken, attempts, maxAttempts);
        return repository;
    }

    private static FulfillmentClaim claim(AttemptOperation operation, String leaseToken) {
        return new FulfillmentClaim(9, "TASK-9", FulfillmentType.COUPON, 11, "{}", operation,
                leaseToken, NOW, NOW.plusSeconds(30));
    }

    private static FulfillmentTaskRecord task(long id, String no, FulfillmentStatus status) {
        return new FulfillmentTaskRecord(
                id, no, "LOTTERY_REWARD", "DRAW-" + id, FulfillmentType.COUPON, 11L,
                "{}", "0".repeat(64), status, 1, 5, NOW
        );
    }

    private static final class FakeRepository implements FulfillmentTaskStateRepository {
        private FulfillmentTaskRecord readyTask;
        private List<FulfillmentTaskRecord> dueTasks = List.of();
        private List<FulfillmentTaskRecord> compensationTasks = List.of();
        private final List<Long> claimFailures = new ArrayList<>();
        private final List<FulfillmentStatus> claimedStatuses = new ArrayList<>();
        private final List<FulfillmentAttemptRecord> attempts = new ArrayList<>();
        private final List<FulfillmentTaskOutcomeUpdate> outcomeUpdates = new ArrayList<>();
        private final List<FulfillmentQuarantineRecord> quarantines = new ArrayList<>();
        private FulfillmentTaskClaimSnapshot claimSnapshot;
        private int lastLimit;
        private int recoveredCount;
        private LocalDateTime compensationNow;
        private LocalDateTime pendingBefore;

        @Override
        public Optional<FulfillmentTaskRecord> lockReady(String fulfillmentNo, LocalDateTime now) {
            return Optional.ofNullable(readyTask)
                    .filter(task -> task.fulfillmentNo().equals(fulfillmentNo));
        }

        @Override
        public List<FulfillmentTaskRecord> lockDue(LocalDateTime now, int limit) {
            lastLimit = limit;
            return dueTasks;
        }

        @Override
        public List<FulfillmentTaskRecord> lockCompensationDue(
                LocalDateTime now, LocalDateTime pendingBefore, int limit) {
            compensationNow = now;
            this.pendingBefore = pendingBefore;
            lastLimit = limit;
            return compensationTasks;
        }

        @Override
        public boolean claim(FulfillmentTaskRecord task, String leaseToken, LocalDateTime leaseUntil) {
            claimedStatuses.add(task.status());
            return !claimFailures.contains(task.id());
        }

        @Override
        public Optional<FulfillmentTaskClaimSnapshot> lockClaim(long taskId) {
            return Optional.ofNullable(claimSnapshot);
        }

        @Override
        public int nextAttemptSequence(long taskId) {
            return attempts.size() + 1;
        }

        @Override
        public void insertAttempt(FulfillmentAttemptRecord attempt) {
            attempts.add(attempt);
        }

        @Override
        public int updateAfterResult(FulfillmentTaskOutcomeUpdate update) {
            outcomeUpdates.add(update);
            return 1;
        }

        @Override
        public void upsertQuarantine(FulfillmentQuarantineRecord quarantine) {
            quarantines.add(quarantine);
        }

        @Override
        public int recoverExpiredLeases(int limit) {
            lastLimit = limit;
            return recoveredCount;
        }
    }
}
