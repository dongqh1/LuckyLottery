package com.dongqh.fulfillmentservice.persistence.shipping;

import com.dongqh.fulfillmentservice.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillmentservice.enums.shipping.ShippingStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcShippingOrderRepository implements ShippingOrderRepository {

    private static final String PROJECTION_CANDIDATE_PREDICATE = """
            ft.fulfillment_type='LOGISTICS'
              AND (
                (ft.status='SUCCEEDED' AND so.status IN ('READY','FULFILLING','FAILED'))
                OR (ft.status='QUARANTINED' AND so.status='FULFILLING')
                OR (ft.status='TERMINATED' AND so.status IN ('READY','FULFILLING','FAILED'))
                OR (ft.status IN ('PENDING','RETRY_WAITING','PROCESSING','RECONCILING')
                    AND so.status='FAILED')
              )
            """;

    private static final String COLUMNS = "id, shipping_no, source_type, source_id, target_user_id, "
            + "address_snapshot_id, hmall_item_id, sku_code, product_name, image_url, quantity, fulfillment_no, "
            + "claim_request_id, carrier_code, carrier_name, waybill_no, status, last_error_code, last_error_message, "
            + "shipped_at, delivered_at, failed_at, terminated_at, version, created_at, updated_at";
    private static final RowMapper<ShippingOrder> ROW_MAPPER = (rs, rowNum) -> new ShippingOrder(
            rs.getLong("id"), rs.getString("shipping_no"),
            ShippingSourceType.valueOf(rs.getString("source_type")), rs.getString("source_id"),
            rs.getLong("target_user_id"), rs.getLong("address_snapshot_id"),
            rs.getObject("hmall_item_id", Long.class), rs.getString("sku_code"), rs.getString("product_name"),
            rs.getString("image_url"), rs.getInt("quantity"), rs.getString("fulfillment_no"),
            rs.getString("claim_request_id"), rs.getString("carrier_code"), rs.getString("carrier_name"),
            rs.getString("waybill_no"), ShippingStatus.valueOf(rs.getString("status")),
            rs.getString("last_error_code"), rs.getString("last_error_message"),
            dateTime(rs, "shipped_at"), dateTime(rs, "delivered_at"), dateTime(rs, "failed_at"),
            dateTime(rs, "terminated_at"), rs.getInt("version"), dateTime(rs, "created_at"),
            dateTime(rs, "updated_at"));

    private final JdbcTemplate jdbc;

    public JdbcShippingOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ShippingOrder> findById(long id) {
        return queryOne("SELECT " + COLUMNS + " FROM shipping_order WHERE id = ?", id);
    }

    @Override
    public List<ShippingOrder> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> normalized = ids.stream().distinct().toList();
        if (normalized.isEmpty() || normalized.size() > 100) {
            throw new IllegalArgumentException("ids必须包含1到100个有效发货单ID");
        }
        if (normalized.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("ids必须包含1到100个有效发货单ID");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(normalized.size(), "?"));
        return jdbc.query("SELECT " + COLUMNS + " FROM shipping_order WHERE id IN (" + placeholders + ") ORDER BY id",
                ROW_MAPPER, normalized.toArray());
    }

    @Override
    public Optional<ShippingOrder> lockById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM shipping_order WHERE id = ? FOR UPDATE",
                ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<ShippingOrder> findByShippingNo(String shippingNo) {
        return queryOne("SELECT " + COLUMNS + " FROM shipping_order WHERE shipping_no = ?", shippingNo);
    }

    @Override
    public Optional<ShippingOrder> lockByShippingNo(String shippingNo) {
        return jdbc.query("SELECT " + COLUMNS + " FROM shipping_order WHERE shipping_no = ? FOR UPDATE",
                ROW_MAPPER, shippingNo).stream().findFirst();
    }

    @Override
    public List<Long> findAdminPage(ShippingStatus status, ShippingSourceType sourceType, String sourceId,
                                    Long targetUserId, String waybillNo, long limit, long offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw new IllegalArgumentException("分页参数无效");
        SqlFilter filter = adminFilter(status, sourceType, sourceId, targetUserId, waybillNo);
        return jdbc.queryForList("SELECT id FROM shipping_order" + filter.where()
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                Long.class, append(filter.parameters(), limit, offset));
    }

    @Override
    public long countAdmin(ShippingStatus status, ShippingSourceType sourceType, String sourceId,
                           Long targetUserId, String waybillNo) {
        SqlFilter filter = adminFilter(status, sourceType, sourceId, targetUserId, waybillNo);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM shipping_order" + filter.where(),
                Long.class, filter.parameters().toArray());
        return total == null ? 0 : total;
    }

    @Override
    public Optional<ShippingOrder> findByFulfillmentNo(String fulfillmentNo) {
        return queryOne("SELECT " + COLUMNS + " FROM shipping_order WHERE fulfillment_no = ?", fulfillmentNo);
    }

    @Override
    public Optional<ShippingOrder> findBySource(ShippingSourceType sourceType, String sourceId) {
        return queryOne("SELECT " + COLUMNS + " FROM shipping_order WHERE source_type = ? AND source_id = ?",
                sourceType.name(), sourceId);
    }

    @Override
    public Optional<ShippingOrder> findByClaimRequestId(String claimRequestId) {
        if (claimRequestId == null) {
            return Optional.empty();
        }
        return queryOne("SELECT " + COLUMNS + " FROM shipping_order WHERE claim_request_id = ?", claimRequestId);
    }

    @Override
    public ShippingOrder insert(ShippingOrder order) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO shipping_order (shipping_no, source_type, source_id, target_user_id, "
                                + "address_snapshot_id, hmall_item_id, sku_code, product_name, image_url, quantity, "
                                + "claim_request_id, status, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'READY', 0)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, order.shippingNo());
                statement.setString(2, order.sourceType().name());
                statement.setString(3, order.sourceId());
                statement.setLong(4, order.targetUserId());
                statement.setLong(5, order.addressSnapshotId());
                if (order.hmallItemId() == null) statement.setNull(6, java.sql.Types.BIGINT);
                else statement.setLong(6, order.hmallItemId());
                statement.setString(7, order.skuCode());
                statement.setString(8, order.productName());
                statement.setString(9, order.imageUrl());
                statement.setInt(10, order.quantity());
                statement.setString(11, order.claimRequestId());
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateShippingOrderException(exception);
        }
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("发货单持久化未返回主键");
        }
        return order.withId(id.longValue());
    }

    @Override
    public boolean markFulfilling(long id, String fulfillmentNo) {
        return jdbc.update("UPDATE shipping_order SET fulfillment_no = ?, status = 'FULFILLING', "
                        + "version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE id = ? AND fulfillment_no IS NULL AND status = 'READY'",
                fulfillmentNo, id) == 1;
    }

    @Override
    public boolean updateProjection(ShippingOrder expected, ShippingOrder projected) {
        if (expected == null || projected == null || expected.id() == null
                || !expected.id().equals(projected.id()) || expected.version() == null
                || expected.status() == null) {
            throw new IllegalArgumentException("发货状态投影参数无效");
        }
        return jdbc.update("""
                UPDATE shipping_order
                SET carrier_code=?, carrier_name=?, waybill_no=?, status=?,
                    last_error_code=?, last_error_message=?, shipped_at=?, failed_at=?,
                    terminated_at=?, version=version+1, updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=? AND version=? AND status=?
                  AND status NOT IN ('DELIVERED','TERMINATED')
                """, projected.carrierCode(), projected.carrierName(), projected.waybillNo(),
                projected.status().name(), projected.lastErrorCode(), projected.lastErrorMessage(),
                projected.shippedAt(), projected.failedAt(), projected.terminatedAt(), expected.id(),
                expected.version(), expected.status().name()) == 1;
    }

    @Override
    public java.util.List<Long> selectProjectionCandidateIdsAfter(long cursorId, int limit) {
        validateProjectionScan(limit);
        return jdbc.queryForList("""
                SELECT so.id
                FROM fulfillment_task ft
                JOIN shipping_order so ON so.fulfillment_no=ft.fulfillment_no
                WHERE so.id > ?
                  AND %s
                ORDER BY so.id ASC
                LIMIT ?
                """.formatted(PROJECTION_CANDIDATE_PREDICATE), Long.class, cursorId, limit);
    }

    @Override
    public java.util.List<Long> selectProjectionCandidateIdsBefore(long cursorId, int limit) {
        validateProjectionScan(limit);
        return jdbc.queryForList("""
                SELECT so.id
                FROM fulfillment_task ft
                JOIN shipping_order so ON so.fulfillment_no=ft.fulfillment_no
                WHERE so.id <= ?
                  AND %s
                ORDER BY so.id ASC
                LIMIT ?
                """.formatted(PROJECTION_CANDIDATE_PREDICATE), Long.class, cursorId, limit);
    }

    @Override
    public long selectProjectionCandidateHighWaterMark() {
        Long highWaterMark = jdbc.queryForObject("""
                SELECT COALESCE(MAX(so.id), 0)
                FROM fulfillment_task ft
                JOIN shipping_order so ON so.fulfillment_no=ft.fulfillment_no
                WHERE %s
                """.formatted(PROJECTION_CANDIDATE_PREDICATE), Long.class);
        return highWaterMark == null ? 0 : highWaterMark;
    }

    @Override
    public java.util.List<Long> selectProjectionCandidateIdsBetween(
            long cursorId,
            long highWaterMarkId,
            int limit
    ) {
        validateProjectionScan(limit);
        if (cursorId < 0 || highWaterMarkId < 0) {
            throw new IllegalArgumentException("投影扫描游标不能为负数");
        }
        if (highWaterMarkId <= cursorId) {
            return java.util.List.of();
        }
        return jdbc.queryForList("""
                SELECT so.id
                FROM fulfillment_task ft
                JOIN shipping_order so ON so.fulfillment_no=ft.fulfillment_no
                WHERE so.id > ? AND so.id <= ?
                  AND %s
                ORDER BY so.id ASC
                LIMIT ?
                """.formatted(PROJECTION_CANDIDATE_PREDICATE),
                Long.class, cursorId, highWaterMarkId, limit);
    }

    private void validateProjectionScan(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit必须为1到1000");
        }
    }

    private Optional<ShippingOrder> queryOne(String sql, Object... args) {
        return jdbc.query(sql, ROW_MAPPER, args).stream().findFirst();
    }

    private SqlFilter adminFilter(ShippingStatus status, ShippingSourceType sourceType, String sourceId,
                                  Long targetUserId, String waybillNo) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        java.util.ArrayList<Object> parameters = new java.util.ArrayList<>();
        if (status != null) { where.append(" AND status=?"); parameters.add(status.name()); }
        if (sourceType != null) { where.append(" AND source_type=?"); parameters.add(sourceType.name()); }
        if (sourceId != null && !sourceId.isBlank()) { where.append(" AND source_id=?"); parameters.add(sourceId.trim()); }
        if (targetUserId != null) { where.append(" AND target_user_id=?"); parameters.add(targetUserId); }
        if (waybillNo != null && !waybillNo.isBlank()) { where.append(" AND waybill_no=?"); parameters.add(waybillNo.trim()); }
        return new SqlFilter(where.toString(), parameters);
    }

    private Object[] append(List<Object> parameters, Object... tail) {
        java.util.ArrayList<Object> values = new java.util.ArrayList<>(parameters);
        java.util.Collections.addAll(values, tail);
        return values.toArray();
    }

    private record SqlFilter(String where, List<Object> parameters) { }

    private static java.time.LocalDateTime dateTime(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
