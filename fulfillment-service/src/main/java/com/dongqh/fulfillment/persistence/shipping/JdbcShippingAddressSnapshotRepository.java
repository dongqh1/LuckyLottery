package com.dongqh.fulfillment.persistence.shipping;

import com.dongqh.fulfillment.enums.shipping.ShippingSourceType;
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
public class JdbcShippingAddressSnapshotRepository implements ShippingAddressSnapshotRepository {

    private static final String COLUMNS = "id, snapshot_no, user_id, address_id, source_type, source_id, "
            + "receiver_ciphertext, phone_ciphertext, province_ciphertext, city_ciphertext, district_ciphertext, "
            + "detail_ciphertext, receiver_masked, phone_masked, region_masked, created_at";
    private static final RowMapper<ShippingAddressSnapshot> ROW_MAPPER = (rs, rowNum) ->
            new ShippingAddressSnapshot(rs.getLong("id"), rs.getString("snapshot_no"), rs.getLong("user_id"),
                    rs.getObject("address_id", Long.class), ShippingSourceType.valueOf(rs.getString("source_type")),
                    rs.getString("source_id"), rs.getString("receiver_ciphertext"), rs.getString("phone_ciphertext"),
                    rs.getString("province_ciphertext"), rs.getString("city_ciphertext"),
                    rs.getString("district_ciphertext"), rs.getString("detail_ciphertext"),
                    rs.getString("receiver_masked"), rs.getString("phone_masked"), rs.getString("region_masked"),
                    rs.getTimestamp("created_at") == null ? null
                            : rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public JdbcShippingAddressSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ShippingAddressSnapshot> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM shipping_address_snapshot WHERE id = ?",
                        ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public List<ShippingAddressSnapshot> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> normalized = ids.stream().distinct().toList();
        if (normalized.isEmpty() || normalized.size() > 100
                || normalized.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("ids必须包含1到100个有效地址快照ID");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(normalized.size(), "?"));
        return jdbc.query("SELECT " + COLUMNS + " FROM shipping_address_snapshot WHERE id IN ("
                        + placeholders + ") ORDER BY id", ROW_MAPPER, normalized.toArray());
    }

    @Override
    public Optional<ShippingAddressSnapshot> findBySource(ShippingSourceType sourceType, String sourceId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM shipping_address_snapshot WHERE source_type = ? AND source_id = ?",
                        ROW_MAPPER, sourceType.name(), sourceId).stream().findFirst();
    }

    @Override
    public ShippingAddressSnapshot insert(ShippingAddressSnapshot snapshot) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO shipping_address_snapshot (snapshot_no, user_id, address_id, source_type, "
                                + "source_id, receiver_ciphertext, phone_ciphertext, province_ciphertext, "
                                + "city_ciphertext, district_ciphertext, detail_ciphertext, receiver_masked, "
                                + "phone_masked, region_masked) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, snapshot.snapshotNo());
                statement.setLong(2, snapshot.userId());
                if (snapshot.addressId() == null) statement.setNull(3, java.sql.Types.BIGINT);
                else statement.setLong(3, snapshot.addressId());
                statement.setString(4, snapshot.sourceType().name());
                statement.setString(5, snapshot.sourceId());
                statement.setString(6, snapshot.receiverCiphertext());
                statement.setString(7, snapshot.phoneCiphertext());
                statement.setString(8, snapshot.provinceCiphertext());
                statement.setString(9, snapshot.cityCiphertext());
                statement.setString(10, snapshot.districtCiphertext());
                statement.setString(11, snapshot.detailCiphertext());
                statement.setString(12, snapshot.receiverMasked());
                statement.setString(13, snapshot.phoneMasked());
                statement.setString(14, snapshot.regionMasked());
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateShippingAddressSnapshotException(exception);
        }
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("地址快照持久化未返回主键");
        }
        return snapshot.withId(id.longValue());
    }
}
