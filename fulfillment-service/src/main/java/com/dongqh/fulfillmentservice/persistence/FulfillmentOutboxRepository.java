package com.dongqh.fulfillmentservice.persistence;

import java.time.LocalDateTime;
import java.util.List;

public interface FulfillmentOutboxRepository {

    List<FulfillmentOutboxCandidate> findRelayCandidates(LocalDateTime now, int limit);

    boolean claimForRelay(long id, String claimToken, LocalDateTime now, LocalDateTime claimUntil);

    int markSent(long id, String claimToken, LocalDateTime sentAt);

    int markFailed(long id, String claimToken, String error, LocalDateTime nextRetryAt);
}
