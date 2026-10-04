package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.model.FulfillmentTaskRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;

@Repository
public class JdbcFulfillmentTaskRepository implements FulfillmentTaskRepository {

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

    private final JdbcTemplate jdbc;

    public JdbcFulfillmentTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FulfillmentTaskRecord> findByFulfillmentNo(String fulfillmentNo) {
        return jdbc.query(
                        "SELECT id, fulfillment_no, source_type, source_id, fulfillment_type, target_user_id, "
                                + "request_payload, request_fingerprint, status, attempt_count, max_attempts, next_attempt_at "
                                + "FROM fulfillment_task WHERE fulfillment_no = ?",
                        ROW_MAPPER,
                        fulfillmentNo
                ).stream()
                .findFirst();
    }

    @Override
    public FulfillmentTaskRecord insert(FulfillmentTaskRecord task) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO fulfillment_task (fulfillment_no, source_type, source_id, fulfillment_type, "
                                + "target_user_id, request_payload, request_fingerprint, status, attempt_count, "
                                + "max_attempts, next_attempt_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                        Statement.RETURN_GENERATED_KEYS
                );
                statement.setString(1, task.fulfillmentNo());
                statement.setString(2, task.sourceType());
                statement.setString(3, task.sourceId());
                statement.setString(4, task.fulfillmentType().name());
                statement.setLong(5, task.targetUserId());
                statement.setString(6, task.requestPayload());
                statement.setString(7, task.requestFingerprint());
                statement.setString(8, task.status().name());
                statement.setInt(9, task.attemptCount());
                statement.setInt(10, task.maxAttempts());
                statement.setTimestamp(11, java.sql.Timestamp.valueOf(task.nextAttemptAt()));
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateFulfillmentTaskException(task.fulfillmentNo(), exception);
        }
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("履约任务持久化未返回主键");
        }
        return task.withId(id.longValue());
    }
}
