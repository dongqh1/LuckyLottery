package com.dongqh.fulfillment.service.shipping;

import com.dongqh.fulfillment.enums.FulfillmentStatus;
import com.dongqh.fulfillment.enums.FulfillmentType;
import com.dongqh.fulfillment.domain.model.FulfillmentTaskView;
import com.dongqh.fulfillment.service.FulfillmentTaskReadService;
import com.dongqh.fulfillment.service.port.FulfillmentStatusChangedOutbox;
import com.dongqh.fulfillment.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillment.enums.shipping.ShippingStatus;
import com.dongqh.fulfillment.persistence.shipping.ShippingOrder;
import com.dongqh.fulfillment.persistence.shipping.ShippingOrderRepository;
import com.dongqh.hmapi.client.TradeClient;
import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import com.dongqh.hmapi.dto.UpdateTradeOrderShippingInfoRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Service
public class ShippingOrderFulfillmentProjector {

    private final ShippingOrderRepository orders;
    private final FulfillmentTaskReadService tasks;
    private final FulfillmentStatusChangedOutbox statusOutbox;
    private final TradeClient trade;
    private final Clock clock;

    public ShippingOrderFulfillmentProjector(
            ShippingOrderRepository orders,
            FulfillmentTaskReadService tasks,
            FulfillmentStatusChangedOutbox statusOutbox,
            TradeClient trade,
            Clock fulfillmentClock
    ) {
        this.orders = orders;
        this.tasks = tasks;
        this.statusOutbox = statusOutbox;
        this.trade = trade;
        this.clock = fulfillmentClock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void projectFulfillmentState(String fulfillmentNo) {
        String normalizedNo = required(fulfillmentNo);
        ShippingOrder order = orders.findByFulfillmentNo(normalizedNo).orElse(null);
        if (order == null || order.status() == ShippingStatus.DELIVERED
                || order.status() == ShippingStatus.TERMINATED) {
            return;
        }

        FulfillmentTaskView task = tasks.get(normalizedNo);
        requireMatchingTask(order, task, normalizedNo);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        ShippingOrder projected = project(order, task, now);
        if (projected == null || !orders.updateProjection(order, projected)) {
            return;
        }

        if (order.sourceType() == ShippingSourceType.CASH_ORDER
                && projected.carrierName() != null && projected.waybillNo() != null) {
            long orderId = positiveOrderId(order.sourceId());
            trade.updateShippingInfo(orderId, new UpdateTradeOrderShippingInfoRequest(
                    projected.carrierName(), projected.waybillNo()));
        }

        if (order.sourceType() == ShippingSourceType.LOTTERY_BENEFIT) {
            statusOutbox.append(new FulfillmentStatusChangedEventV1(
                    UUID.randomUUID().toString(), normalizedNo, order.shippingNo(), order.sourceType().name(),
                    order.sourceId(), projected.status().name(), now));
        }
    }

    private ShippingOrder project(ShippingOrder order, FulfillmentTaskView task, LocalDateTime now) {
        ShippingStatus status = order.status();
        String carrierCode = order.carrierCode();
        String carrierName = order.carrierName();
        String waybillNo = order.waybillNo();
        String errorCode = order.lastErrorCode();
        String errorMessage = order.lastErrorMessage();
        LocalDateTime shippedAt = order.shippedAt();
        LocalDateTime failedAt = order.failedAt();
        LocalDateTime terminatedAt = order.terminatedAt();

        if (task.status() == FulfillmentStatus.SUCCEEDED) {
            if (status == ShippingStatus.SHIPPED || status == ShippingStatus.IN_TRANSIT
                    || status == ShippingStatus.DELIVERED) {
                return null;
            }
            carrierCode = "SIMULATOR";
            carrierName = "模拟物流";
            waybillNo = bounded(task.externalReference(), 100);
            status = ShippingStatus.SHIPPED;
            shippedAt = shippedAt == null ? now : shippedAt;
            errorCode = null;
            errorMessage = null;
            failedAt = null;
        } else if (task.status() == FulfillmentStatus.QUARANTINED) {
            if (status != ShippingStatus.FULFILLING) {
                return null;
            }
            status = ShippingStatus.FAILED;
            errorCode = defaulted(task.lastErrorCode(), "FULFILLMENT_QUARANTINED", 64);
            errorMessage = defaulted(task.lastErrorMessage(), "物流履约已安全隔离", 500);
            failedAt = now;
        } else if (task.status() == FulfillmentStatus.TERMINATED) {
            if (status == ShippingStatus.SHIPPED || status == ShippingStatus.IN_TRANSIT
                    || status == ShippingStatus.DELIVERED) {
                return null;
            }
            status = ShippingStatus.TERMINATED;
            errorCode = defaulted(task.lastErrorCode(), "FULFILLMENT_TERMINATED", 64);
            errorMessage = defaulted(task.lastErrorMessage(), "物流履约已安全终止", 500);
            terminatedAt = now;
        } else if (status == ShippingStatus.FAILED) {
            status = ShippingStatus.FULFILLING;
            errorCode = null;
            errorMessage = null;
            failedAt = null;
        } else {
            return null;
        }

        return new ShippingOrder(order.id(), order.shippingNo(), order.sourceType(), order.sourceId(),
                order.targetUserId(), order.addressSnapshotId(), order.hmallItemId(), order.skuCode(),
                order.productName(), order.imageUrl(), order.quantity(), order.fulfillmentNo(), order.claimRequestId(),
                carrierCode, carrierName, waybillNo, status, errorCode, errorMessage, shippedAt, order.deliveredAt(),
                failedAt, terminatedAt, order.version(), order.createdAt(), order.updatedAt());
    }

    private void requireMatchingTask(ShippingOrder order, FulfillmentTaskView task, String fulfillmentNo) {
        if (task.fulfillmentType() != FulfillmentType.LOGISTICS
                || !Objects.equals(task.fulfillmentNo(), fulfillmentNo)
                || !Objects.equals(task.sourceType(), order.sourceType().name())
                || !Objects.equals(task.sourceId(), order.sourceId())
                || !Objects.equals(task.targetUserId(), order.targetUserId())) {
            throw new IllegalStateException("履约任务与发货单身份不匹配");
        }
    }

    private String required(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new IllegalArgumentException("fulfillmentNo参数无效");
        }
        return value.trim();
    }

    private String bounded(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.substring(0, Math.min(max, normalized.length()));
    }

    private String defaulted(String value, String fallback, int max) {
        String normalized = bounded(value, max);
        return normalized == null ? fallback : normalized;
    }

    private long positiveOrderId(String sourceId) {
        try {
            long orderId = Long.parseLong(sourceId);
            if (orderId > 0) return orderId;
        } catch (RuntimeException ignored) {
            // Invalid source IDs must not be sent to the trade service.
        }
        throw new IllegalStateException("现金订单物流来源 ID 无效");
    }
}
