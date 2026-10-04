package com.dongqh.fulfillmentservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JdbcFulfillmentOutboxRepository implements FulfillmentOutboxRepository {

    private final JdbcTemplate jdbc;

    public JdbcFulfillmentOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FulfillmentOutboxCandidate> findRelayCandidates(LocalDateTime now, int limit) {
        return jdbc.query(
                "SELECT id, event_type, payload, retry_count FROM fulfillment_outbox "
                        + "WHERE event_type IN ('FULFILLMENT_TASK_READY', 'FULFILLMENT_STATUS_CHANGED_V1') "
                        + "AND (status = 'PENDING' "
                        + "OR (status = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= ?)) "
                        + "OR (status = 'PROCESSING' AND claim_until <= ?)) "
                        + "ORDER BY id LIMIT ?",
                (rs, rowNum) -> new FulfillmentOutboxCandidate(
                        rs.getLong("id"), rs.getString("event_type"),
                        rs.getString("payload"), rs.getLong("retry_count")),
                now,
                now,
                limit
        );
    }

    @Override
    public boolean claimForRelay(long id, String claimToken, LocalDateTime now, LocalDateTime claimUntil) {
        return jdbc.update(
                "UPDATE fulfillment_outbox SET status='PROCESSING', claim_token=?, claim_until=? "
                        + "WHERE id=? AND event_type IN ('FULFILLMENT_TASK_READY', 'FULFILLMENT_STATUS_CHANGED_V1') "
                        + "AND (status='PENDING' "
                        + "OR (status='FAILED' AND (next_retry_at IS NULL OR next_retry_at <= ?)) "
                        + "OR (status='PROCESSING' AND claim_until <= ?))",
                claimToken,
                claimUntil,
                id,
                now,
                now
        ) == 1;
    }

    @Override
    public int markSent(long id, String claimToken, LocalDateTime sentAt) {
        return jdbc.update(
                "UPDATE fulfillment_outbox SET status='SENT', sent_at=?, last_error=NULL, next_retry_at=NULL, "
                        + "claim_token=NULL, claim_until=NULL "
                        + "WHERE id=? AND status='PROCESSING' AND claim_token=?",
                sentAt,
                id,
                claimToken
        );
    }

    @Override
    public int markFailed(long id, String claimToken, String error, LocalDateTime nextRetryAt) {
        return jdbc.update(
                "UPDATE fulfillment_outbox SET status='FAILED', retry_count=retry_count+1, last_error=?, "
                        + "next_retry_at=?, claim_token=NULL, claim_until=NULL "
                        + "WHERE id=? AND status='PROCESSING' AND claim_token=?",
                error,
                nextRetryAt,
                id,
                claimToken
        );
    }
}
