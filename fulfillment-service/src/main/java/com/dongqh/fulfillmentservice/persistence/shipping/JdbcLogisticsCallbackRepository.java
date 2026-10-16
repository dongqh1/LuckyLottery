package com.dongqh.fulfillmentservice.persistence.shipping;

import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import com.dongqh.fulfillmentservice.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillmentservice.enums.shipping.ShippingStatus;
import com.dongqh.fulfillmentservice.enums.shipping.TrackingEventType;
import com.dongqh.fulfillmentservice.service.port.FulfillmentStatusChangedOutbox;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JdbcLogisticsCallbackRepository implements LogisticsCallbackRepository {
    private final JdbcTemplate jdbc;
    private final FulfillmentStatusChangedOutbox statusOutbox;

    public JdbcLogisticsCallbackRepository(JdbcTemplate jdbc, FulfillmentStatusChangedOutbox statusOutbox) {
        this.jdbc = jdbc;
        this.statusOutbox = statusOutbox;
    }

    @Override
    public void insertReceipt(ShippingCallbackReceipt receipt) {
        jdbc.update("""
                INSERT INTO shipping_callback_receipt
                    (callback_id, nonce_digest, signature_digest, waybill_no, event_type, event_time,
                     status, received_at)
                VALUES (?, ?, ?, ?, ?, ?, 'RECEIVED', ?)
                """, receipt.callbackId(), receipt.nonceDigest(), receipt.signatureDigest(),
                receipt.waybillNo(), receipt.eventType().name(), receipt.eventTime(), receipt.receivedAt());
    }

    @Override
    public ShippingCallbackReceipt selectReceipt(String callbackId) {
        return jdbc.query("""
                SELECT callback_id, nonce_digest, signature_digest, waybill_no, event_type, event_time,
                       status, error_code, received_at
                FROM shipping_callback_receipt WHERE callback_id=?
                """, rs -> rs.next() ? new ShippingCallbackReceipt(
                rs.getString("callback_id"), rs.getString("nonce_digest"), rs.getString("signature_digest"),
                rs.getString("waybill_no"), TrackingEventType.valueOf(rs.getString("event_type")),
                localDateTime(rs.getTimestamp("event_time")), rs.getString("status"), rs.getString("error_code"),
                localDateTime(rs.getTimestamp("received_at"))) : null, callbackId);
    }

    @Override
    public ShippingCallbackOrder lockOrderByWaybillNo(String waybillNo) {
        return jdbc.query("""
                SELECT id, shipping_no, source_type, source_id, fulfillment_no, waybill_no,
                       status, delivered_at, version
                FROM shipping_order WHERE waybill_no=? FOR UPDATE
                """, rs -> rs.next() ? new ShippingCallbackOrder(
                rs.getLong("id"), rs.getString("shipping_no"),
                ShippingSourceType.valueOf(rs.getString("source_type")), rs.getString("source_id"),
                rs.getString("fulfillment_no"), rs.getString("waybill_no"),
                ShippingStatus.valueOf(rs.getString("status")),
                localDateTime(rs.getTimestamp("delivered_at")), rs.getInt("version")) : null, waybillNo);
    }

    @Override
    public boolean insertTrackingEvent(ShippingTrackingEvent event) {
        try {
            jdbc.update("""
                    INSERT INTO shipping_tracking_event
                        (shipping_order_id, provider_event_id, waybill_no, event_type, location_summary,
                         description, event_time, received_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, event.shippingOrderId(), event.providerEventId(), event.waybillNo(),
                    event.eventType().name(), event.locationSummary(), event.description(),
                    event.eventTime(), event.receivedAt());
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public List<ShippingTrackingEvent> findTrackingByShippingOrderId(long shippingOrderId) {
        if (shippingOrderId <= 0) {
            return List.of();
        }
        return jdbc.query("""
                SELECT shipping_order_id, provider_event_id, waybill_no, event_type, location_summary,
                       description, event_time, received_at
                FROM shipping_tracking_event
                WHERE shipping_order_id=?
                ORDER BY event_time, id
                """, (rs, rowNum) -> new ShippingTrackingEvent(
                rs.getLong("shipping_order_id"), rs.getString("provider_event_id"), rs.getString("waybill_no"),
                TrackingEventType.valueOf(rs.getString("event_type")), rs.getString("location_summary"),
                rs.getString("description"), localDateTime(rs.getTimestamp("event_time")),
                localDateTime(rs.getTimestamp("received_at"))), shippingOrderId);
    }

    @Override
    public boolean advanceOrderStatus(ShippingCallbackOrder order, ShippingStatus target, LocalDateTime deliveredAt) {
        return jdbc.update("""
                UPDATE shipping_order
                SET status=?, delivered_at=?, version=version+1, updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=? AND version=? AND status=?
                """, target.name(), deliveredAt, order.id(), order.version(), order.status().name()) == 1;
    }

    @Override
    public void markReceiptProcessed(String callbackId, LocalDateTime processedAt) {
        jdbc.update("""
                UPDATE shipping_callback_receipt SET status='PROCESSED', processed_at=? WHERE callback_id=?
                """, processedAt, callbackId);
    }

    @Override
    public void markReceiptRejected(
            String callbackId, String errorCode, String errorMessage, LocalDateTime processedAt
    ) {
        jdbc.update("""
                UPDATE shipping_callback_receipt
                SET status='REJECTED', error_code=?, error_message=?, processed_at=? WHERE callback_id=?
                """, errorCode, errorMessage, processedAt, callbackId);
    }

    @Override
    public void appendStatusChanged(FulfillmentStatusChangedEventV1 event) {
        statusOutbox.append(event);
    }

    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
