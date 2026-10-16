package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FulfillmentTaskStateRepository {

    Optional<FulfillmentTaskRecord> lockReady(String fulfillmentNo, LocalDateTime now);

    List<FulfillmentTaskRecord> lockDue(LocalDateTime now, int limit);

    List<FulfillmentTaskRecord> lockCompensationDue(
            LocalDateTime now,
            LocalDateTime pendingBefore,
            int limit
    );

    boolean claim(FulfillmentTaskRecord task, String leaseToken, LocalDateTime leaseUntil);

    Optional<FulfillmentTaskClaimSnapshot> lockClaim(long taskId);

    int nextAttemptSequence(long taskId);

    void insertAttempt(FulfillmentAttemptRecord attempt);

    int updateAfterResult(FulfillmentTaskOutcomeUpdate update);

    void upsertQuarantine(FulfillmentQuarantineRecord quarantine);

    int recoverExpiredLeases(int limit);
}
