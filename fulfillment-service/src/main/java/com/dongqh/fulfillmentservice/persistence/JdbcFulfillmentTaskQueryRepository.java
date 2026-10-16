package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.enums.FailureCategory;
import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcFulfillmentTaskQueryRepository implements FulfillmentTaskQueryRepository {

    private static final String COLUMNS = "id, fulfillment_no, source_type, source_id, fulfillment_type, "
            + "target_user_id, request_payload, request_fingerprint, status, attempt_count, max_attempts, "
            + "next_attempt_at, external_reference, last_error_category, last_error_code, last_error_message, "
            + "completed_at, created_at, updated_at";

    private static final RowMapper<FulfillmentTaskDetails> ROW_MAPPER = (rs, rowNum) -> new FulfillmentTaskDetails(
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
            dateTime(rs.getTimestamp("next_attempt_at")),
            rs.getString("external_reference"),
            failureCategory(rs.getString("last_error_category")),
            rs.getString("last_error_code"),
            rs.getString("last_error_message"),
            dateTime(rs.getTimestamp("completed_at")),
            dateTime(rs.getTimestamp("created_at")),
            dateTime(rs.getTimestamp("updated_at"))
    );

    private final JdbcTemplate jdbc;

    public JdbcFulfillmentTaskQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FulfillmentTaskDetails> findByFulfillmentNo(String fulfillmentNo) {
        return jdbc.query(
                        "SELECT " + COLUMNS + " FROM fulfillment_task WHERE fulfillment_no=?",
                        ROW_MAPPER,
                        fulfillmentNo
                ).stream()
                .findFirst();
    }

    @Override
    public List<FulfillmentTaskStatus> findLotteryStatusByFulfillmentNos(Collection<String> fulfillmentNos) {
        if (fulfillmentNos == null || fulfillmentNos.isEmpty()) {
            return List.of();
        }
        List<String> normalized = fulfillmentNos.stream().distinct().toList();
        if (normalized.size() > 100 || normalized.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("fulfillmentNos must contain at most 100 non-blank values");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(normalized.size(), "?"));
        return jdbc.query("SELECT fulfillment_no, status FROM fulfillment_task "
                        + "WHERE source_type='LOTTERY_BENEFIT' AND fulfillment_no IN (" + placeholders + ")",
                (rs, rowNum) -> new FulfillmentTaskStatus(rs.getString("fulfillment_no"),
                        FulfillmentStatus.valueOf(rs.getString("status"))), normalized.toArray());
    }

    @Override
    public List<FulfillmentTaskDetails> findPage(FulfillmentTaskQuery query) {
        QueryFilter filter = filter(query);
        long offset;
        try {
            offset = Math.multiplyExact(query.page() - 1, query.size());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("分页偏移量超出范围", exception);
        }
        List<Object> parameters = new ArrayList<>(filter.parameters());
        parameters.add(query.size());
        parameters.add(offset);
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM fulfillment_task" + filter.whereClause()
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER,
                parameters.toArray()
        );
    }

    @Override
    public long count(FulfillmentTaskQuery query) {
        QueryFilter filter = filter(query);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM fulfillment_task" + filter.whereClause(),
                Long.class,
                filter.parameters().toArray()
        );
        return count == null ? 0 : count;
    }

    private QueryFilter filter(FulfillmentTaskQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (query.fulfillmentType() != null) {
            where.append(" AND fulfillment_type=?");
            parameters.add(query.fulfillmentType().name());
        }
        if (query.status() != null) {
            where.append(" AND status=?");
            parameters.add(query.status().name());
        }
        if (query.targetUserId() != null) {
            where.append(" AND target_user_id=?");
            parameters.add(query.targetUserId());
        }
        if (hasText(query.sourceType())) {
            where.append(" AND source_type=?");
            parameters.add(query.sourceType());
        }
        if (hasText(query.sourceId())) {
            where.append(" AND source_id=?");
            parameters.add(query.sourceId());
        }
        return new QueryFilter(where.toString(), List.copyOf(parameters));
    }

    private static java.time.LocalDateTime dateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static FailureCategory failureCategory(String value) {
        return value == null ? null : FailureCategory.valueOf(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record QueryFilter(String whereClause, List<Object> parameters) {
    }
}
