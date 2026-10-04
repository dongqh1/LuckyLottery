package com.dongqh.fulfillmentservice.service;

import com.dongqh.fulfillmentservice.enums.AttemptOperation;
import com.dongqh.fulfillmentservice.enums.FailureCategory;
import com.dongqh.fulfillmentservice.enums.GatewayOutcome;
import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.domain.FulfillmentTransition;
import com.dongqh.fulfillmentservice.domain.FulfillmentTransitionPolicy;
import com.dongqh.fulfillmentservice.model.FulfillmentClaim;
import com.dongqh.fulfillmentservice.model.FulfillmentGatewayResult;
import com.dongqh.fulfillmentservice.model.FulfillmentTaskRecord;
import com.dongqh.fulfillmentservice.persistence.FulfillmentAttemptRecord;
import com.dongqh.fulfillmentservice.persistence.FulfillmentQuarantineRecord;
import com.dongqh.fulfillmentservice.persistence.FulfillmentTaskClaimSnapshot;
import com.dongqh.fulfillmentservice.persistence.FulfillmentTaskOutcomeUpdate;
import com.dongqh.fulfillmentservice.persistence.FulfillmentTaskStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FulfillmentTaskStateService {

    private final FulfillmentTaskStateRepository repository;
    private final Clock clock;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final FulfillmentTaskReadService taskReadService;
    private final FulfillmentStatusChangedRecorder statusChangedRecorder;
    private final FulfillmentTransitionPolicy transitionPolicy = new FulfillmentTransitionPolicy();

    @Autowired
    public FulfillmentTaskStateService(
            FulfillmentTaskStateRepository repository,
            Clock fulfillmentClock,
            @org.springframework.beans.factory.annotation.Value("${luckyhub.fulfillment.base-delay:1s}")
            Duration baseDelay,
            @org.springframework.beans.factory.annotation.Value("${luckyhub.fulfillment.max-delay:1m}")
            Duration maxDelay,
            FulfillmentTaskReadService taskReadService,
            FulfillmentStatusChangedRecorder statusChangedRecorder
    ) {
        if (baseDelay == null || baseDelay.isZero() || baseDelay.isNegative()
                || maxDelay == null || maxDelay.compareTo(baseDelay) < 0) {
            throw new IllegalArgumentException("履约退避时间配置无效");
        }
        this.repository = repository;
        this.clock = fulfillmentClock;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.taskReadService = taskReadService;
        this.statusChangedRecorder = statusChangedRecorder;
    }

    public FulfillmentTaskStateService(FulfillmentTaskStateRepository repository, Clock fulfillmentClock,
                                       Duration baseDelay, Duration maxDelay) {
        this(repository, fulfillmentClock, baseDelay, maxDelay, null, null);
    }

    @Transactional
    public Optional<FulfillmentClaim> claimReady(String fulfillmentNo, Duration leaseDuration) {
        if (fulfillmentNo == null || fulfillmentNo.isBlank()) {
            throw new IllegalArgumentException("fulfillmentNo不能为空");
        }
        validateLease(leaseDuration);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime until = now.plus(leaseDuration);
        return repository.lockReady(fulfillmentNo.trim(), now)
                .flatMap(task -> claim(task, now, until));
    }

    @Transactional
    public List<FulfillmentClaim> claimDue(int limit, Duration leaseDuration) {
        validate(limit, leaseDuration);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime until = now.plus(leaseDuration);
        List<FulfillmentClaim> claims = new ArrayList<>();
        for (FulfillmentTaskRecord task : repository.lockDue(now, limit)) {
            claim(task, now, until).ifPresent(claims::add);
        }
        return List.copyOf(claims);
    }

    @Transactional
    public List<FulfillmentClaim> claimCompensationDue(
            int limit,
            Duration leaseDuration,
            Duration pendingGrace
    ) {
        validate(limit, leaseDuration);
        if (pendingGrace == null || pendingGrace.isZero() || pendingGrace.isNegative()) {
            throw new IllegalArgumentException("pendingGrace必须为正数");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime until = now.plus(leaseDuration);
        LocalDateTime pendingBefore = now.minus(pendingGrace);
        List<FulfillmentClaim> claims = new ArrayList<>();
        for (FulfillmentTaskRecord task : repository.lockCompensationDue(now, pendingBefore, limit)) {
            claim(task, now, until).ifPresent(claims::add);
        }
        return List.copyOf(claims);
    }

    @Transactional
    public void recordResult(FulfillmentClaim claim, FulfillmentGatewayResult result, long durationMs) {
        if (claim == null || result == null || durationMs < 0) {
            throw new IllegalArgumentException("结果参数不合法");
        }
        FulfillmentTaskClaimSnapshot task = repository.lockClaim(claim.taskId())
                .orElseThrow(() -> new FulfillmentLeaseConflictException(claim.taskId()));
        if (task.status() != FulfillmentStatus.PROCESSING
                || !java.util.Objects.equals(task.leaseToken(), claim.leaseToken())) {
            throw new FulfillmentLeaseConflictException(claim.taskId());
        }

        int executionAttempts = task.attemptCount()
                + (claim.operation() == AttemptOperation.EXECUTE ? 1 : 0);
        LocalDateTime finishedAt = LocalDateTime.now(clock);
        FailureCategory category = category(result.outcome());
        int sequenceNo = repository.nextAttemptSequence(claim.taskId());
        repository.insertAttempt(new FulfillmentAttemptRecord(
                claim.taskId(),
                claim.fulfillmentNo(),
                sequenceNo,
                claim.operation(),
                result.outcome(),
                claim.claimedAt(),
                finishedAt,
                durationMs,
                result.externalReference(),
                category,
                result.errorCode(),
                result.safeMessage()
        ));

        FulfillmentTransition transition = transitionPolicy.resolve(
                result.outcome(),
                claim.operation(),
                executionAttempts,
                task.maxAttempts(),
                finishedAt,
                baseDelay,
                maxDelay
        );
        FulfillmentTaskOutcomeUpdate update = new FulfillmentTaskOutcomeUpdate(
                claim.taskId(),
                claim.leaseToken(),
                transition.status(),
                executionAttempts,
                transition.nextAttemptAt(),
                result.externalReference(),
                category,
                result.errorCode(),
                result.safeMessage(),
                transition.completedAt()
        );
        if (repository.updateAfterResult(update) != 1) {
            throw new FulfillmentLeaseConflictException(claim.taskId());
        }
        if (transition.status() == FulfillmentStatus.QUARANTINED) {
            repository.upsertQuarantine(new FulfillmentQuarantineRecord(
                    claim.taskId(),
                    claim.fulfillmentNo(),
                    "履约失败",
                    category,
                    result.errorCode(),
                    result.safeMessage(),
                    finishedAt
            ));
        }
        if (statusChangedRecorder != null
                && (transition.status() == FulfillmentStatus.SUCCEEDED
                || transition.status() == FulfillmentStatus.QUARANTINED)) {
            statusChangedRecorder.recordLotteryAssetTask(taskReadService.get(claim.fulfillmentNo()));
        }
    }

    @Transactional
    public int recoverExpiredLeases(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit必须为1到1000");
        }
        return repository.recoverExpiredLeases(limit);
    }

    private Optional<FulfillmentClaim> claim(
            FulfillmentTaskRecord task,
            LocalDateTime now,
            LocalDateTime until
    ) {
        String token = UUID.randomUUID().toString();
        if (!repository.claim(task, token, until)) {
            return Optional.empty();
        }
        AttemptOperation operation = task.status() == FulfillmentStatus.RECONCILING
                ? AttemptOperation.QUERY
                : AttemptOperation.EXECUTE;
        return Optional.of(new FulfillmentClaim(
                task.id(),
                task.fulfillmentNo(),
                task.fulfillmentType(),
                task.targetUserId(),
                task.requestPayload(),
                operation,
                token,
                now,
                until
        ));
    }

    private void validate(int limit, Duration leaseDuration) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit必须为1到1000");
        }
        validateLease(leaseDuration);
    }

    private void validateLease(Duration leaseDuration) {
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration必须为正数");
        }
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
