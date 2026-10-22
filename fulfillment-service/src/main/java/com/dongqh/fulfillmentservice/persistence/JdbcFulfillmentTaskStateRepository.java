package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcFulfillmentTaskStateRepository implements FulfillmentTaskStateRepository {

    private static final RowMapper<FulfillmentTaskRecord> ROW_MAPPER = (rs, rowNum) -> new FulfillmentTaskRecord(
            rs.getLong("id"),
            rs.getString("fulfillment_no"),
            rs.getString("source_type"),
            rs.getString("source_id"),
            FulfillmentType.valueOf(rs.getString("fulfillment_type")),
            rs.getLong("target_user_id"),
            rs.getString("request_payload"),
            rs.getString("request_fingerprint"),
            FulfillmentStatus.valueOf(rs.getString("status")),
            rs.getInt("attempt_count"),
            rs.getInt("max_attempts"),
            rs.getTimestamp("next_attempt_at") == null
                    ? null
                    : rs.getTimestamp("next_attempt_at").toLocalDateTime()
    );

    private static final String TASK_COLUMNS = "id, fulfillment_no, source_type, source_id, fulfillment_type, "
            + "target_user_id, request_payload, request_fingerprint, status, attempt_count, max_attempts, "
            + "next_attempt_at";

    private final JdbcTemplate jdbc;

    public JdbcFulfillmentTaskStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FulfillmentTaskRecord> lockReady(String fulfillmentNo, LocalDateTime now) {
        return jdbc.query(
                        "SELECT " + TASK_COLUMNS + " FROM fulfillment_task "
                                + "WHERE fulfillment_type='LOGISTICS' AND fulfillment_no=? "
                                + "AND status='PENDING' AND (next_attempt_at IS NULL OR next_attempt_at<=?) "
                                + "FOR UPDATE",
                        ROW_MAPPER,
                        fulfillmentNo,
                        Timestamp.valueOf(now)
                ).stream()
                .findFirst();
    }

    @Override
    public List<FulfillmentTaskRecord> lockDue(LocalDateTime now, int limit) {
        return jdbc.query(
                "SELECT " + TASK_COLUMNS + " FROM fulfillment_task "
                        + "WHERE fulfillment_type='LOGISTICS' AND status IN ('RETRY_WAITING','RECONCILING') "
                        + "AND (next_attempt_at IS NULL OR next_attempt_at<=?) "
                        + "ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED",
                ROW_MAPPER,
                Timestamp.valueOf(now),
                limit
        );
    }

    @Override
    public List<FulfillmentTaskRecord> lockCompensationDue(
            LocalDateTime now,
            LocalDateTime pendingBefore,
            int limit
    ) {
        return jdbc.query(
                "SELECT " + TASK_COLUMNS + " FROM fulfillment_task "
                        + "WHERE fulfillment_type='LOGISTICS' AND status='PENDING' "
                        + "AND (next_attempt_at IS NULL OR next_attempt_at<=?) "
                        + "AND created_at<=? ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED",
                ROW_MAPPER,
                Timestamp.valueOf(now),
                Timestamp.valueOf(pendingBefore),
                limit
        );
    }

    @Override
    public boolean claim(FulfillmentTaskRecord task, String leaseToken, LocalDateTime leaseUntil) {
        return jdbc.update(
                "UPDATE fulfillment_task SET status='PROCESSING', lease_token=?, lease_until=? "
                        + "WHERE id=? AND status=?",
                leaseToken,
                Timestamp.valueOf(leaseUntil),
                task.id(),
                task.status().name()
        ) == 1;
    }

    @Override
    public Optional<FulfillmentTaskClaimSnapshot> lockClaim(long taskId) {
        return jdbc.query(
                        "SELECT status, lease_token, attempt_count, max_attempts "
                                + "FROM fulfillment_task WHERE id=? FOR UPDATE",
                        (rs, rowNum) -> new FulfillmentTaskClaimSnapshot(
                                FulfillmentStatus.valueOf(rs.getString("status")),
                                rs.getString("lease_token"),
                                rs.getInt("attempt_count"),
                                rs.getInt("max_attempts")),
                        taskId
                ).stream()
                .findFirst();
    }

    @Override
    public int nextAttemptSequence(long taskId) {
        Integer sequence = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sequence_no), 0) + 1 FROM fulfillment_attempt WHERE task_id=?",
                Integer.class,
                taskId
        );
        if (sequence == null) {
            throw new IllegalStateException("履约尝试序号计算失败");
        }
        return sequence;
    }

    @Override
    public void insertAttempt(FulfillmentAttemptRecord attempt) {
        jdbc.update(
                "INSERT INTO fulfillment_attempt (task_id, fulfillment_no, sequence_no, operation, outcome, "
                        + "started_at, finished_at, duration_ms, external_reference, error_category, error_code, "
                        + "error_message) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                attempt.taskId(),
                attempt.fulfillmentNo(),
                attempt.sequenceNo(),
                attempt.operation().name(),
                attempt.outcome().name(),
                Timestamp.valueOf(attempt.startedAt()),
                Timestamp.valueOf(attempt.finishedAt()),
                attempt.durationMs(),
                attempt.externalReference(),
                attempt.errorCategory() == null ? null : attempt.errorCategory().name(),
                attempt.errorCode(),
                attempt.errorMessage()
        );
    }

    @Override
    public int updateAfterResult(FulfillmentTaskOutcomeUpdate update) {
        return jdbc.update(
                "UPDATE fulfillment_task SET status=?, attempt_count=?, next_attempt_at=?, lease_token=NULL, "
                        + "lease_until=NULL, external_reference=COALESCE(?, external_reference), "
                        + "last_error_category=?, last_error_code=?, last_error_message=?, completed_at=? "
                        + "WHERE id=? AND status='PROCESSING' AND lease_token=?",
                update.status().name(),
                update.attemptCount(),
                timestamp(update.nextAttemptAt()),
                update.externalReference(),
                update.errorCategory() == null ? null : update.errorCategory().name(),
                update.errorCode(),
                update.errorMessage(),
                timestamp(update.completedAt()),
                update.taskId(),
                update.leaseToken()
        );
    }

    @Override
    public void upsertQuarantine(FulfillmentQuarantineRecord quarantine) {
        jdbc.update(
                "INSERT INTO fulfillment_quarantine (task_id, fulfillment_no, reason, error_category, error_code, "
                        + "error_message, quarantined_at) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE reason=VALUES(reason), error_category=VALUES(error_category), "
                        + "error_code=VALUES(error_code), error_message=VALUES(error_message), "
                        + "quarantined_at=VALUES(quarantined_at), resolved_at=NULL, resolution=NULL, "
                        + "resolved_by=NULL, resolution_note=NULL",
                quarantine.taskId(),
                quarantine.fulfillmentNo(),
                quarantine.reason(),
                quarantine.errorCategory() == null ? null : quarantine.errorCategory().name(),
                quarantine.errorCode(),
                quarantine.errorMessage(),
                Timestamp.valueOf(quarantine.quarantinedAt())
        );
    }

    @Override
    public int recoverExpiredLeases(int limit) {
        return jdbc.update(
                "UPDATE fulfillment_task SET status='RECONCILING', next_attempt_at=NOW(3), lease_token=NULL, "
                        + "lease_until=NULL, last_error_category='UNKNOWN', last_error_code='LEASE_EXPIRED', "
                        + "last_error_message='履约租约过期，转入安全对账' "
                        + "WHERE fulfillment_type='LOGISTICS' AND status='PROCESSING' "
                        + "AND lease_until<NOW(3) ORDER BY id LIMIT ?",
                limit
        );
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
