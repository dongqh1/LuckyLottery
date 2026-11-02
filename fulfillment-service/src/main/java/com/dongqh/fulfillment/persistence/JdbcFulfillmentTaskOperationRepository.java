package com.dongqh.fulfillment.persistence;

import com.dongqh.fulfillment.enums.FulfillmentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcFulfillmentTaskOperationRepository implements FulfillmentTaskOperationRepository {

    private final JdbcTemplate jdbc;

    public JdbcFulfillmentTaskOperationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FulfillmentTaskOperationSnapshot> lockByFulfillmentNo(String fulfillmentNo) {
        return jdbc.query(
                        "SELECT id, status FROM fulfillment_task WHERE fulfillment_no=? FOR UPDATE",
                        (rs, rowNum) -> new FulfillmentTaskOperationSnapshot(
                                rs.getLong("id"), FulfillmentStatus.valueOf(rs.getString("status"))),
                        fulfillmentNo
                ).stream()
                .findFirst();
    }

    @Override
    public int retryQuarantined(long taskId) {
        return jdbc.update(
                "UPDATE fulfillment_task SET status='PENDING', next_attempt_at=NOW(3), "
                        + "max_attempts=LEAST(100, attempt_count+5), lease_token=NULL, lease_until=NULL, "
                        + "last_error_category=NULL, last_error_code=NULL, last_error_message=NULL WHERE id=?",
                taskId
        );
    }

    @Override
    public int terminate(long taskId) {
        return jdbc.update(
                "UPDATE fulfillment_task SET status='TERMINATED', next_attempt_at=NULL, lease_token=NULL, "
                        + "lease_until=NULL, completed_at=NOW(3) WHERE id=?",
                taskId
        );
    }

    @Override
    public void resolveQuarantine(long taskId, String resolution, long operatorId, String note) {
        jdbc.update(
                "UPDATE fulfillment_quarantine SET resolved_at=NOW(3), resolution=?, resolved_by=?, "
                        + "resolution_note=? WHERE task_id=?",
                resolution,
                operatorId,
                note,
                taskId
        );
    }
}
