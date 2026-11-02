package com.dongqh.fulfillment.service.shipping;

import com.dongqh.fulfillment.domain.model.shipping.AddShippingTrackingEventCommand;
import com.dongqh.fulfillment.domain.model.shipping.UpdateShippingLogisticsCommand;
import com.dongqh.fulfillment.domain.shipping.ShippingDomainException;
import com.dongqh.fulfillment.domain.shipping.ShippingErrorCode;
import com.dongqh.fulfillment.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillment.enums.shipping.ShippingStatus;
import com.dongqh.fulfillment.enums.shipping.TrackingEventType;
import com.dongqh.fulfillment.persistence.shipping.LogisticsCallbackRepository;
import com.dongqh.fulfillment.persistence.shipping.ShippingCallbackOrder;
import com.dongqh.fulfillment.persistence.shipping.ShippingOrder;
import com.dongqh.fulfillment.persistence.shipping.ShippingOrderRepository;
import com.dongqh.fulfillment.persistence.shipping.ShippingTrackingEvent;
import com.dongqh.fulfillment.service.port.FulfillmentStatusChangedOutbox;
import com.dongqh.hmapi.client.TradeClient;
import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import com.dongqh.hmapi.dto.UpdateTradeOrderShippingInfoRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ShippingAdminMutationService {
    private static final Pattern UNSAFE_TRACKING_TEXT = Pattern.compile("(?i)(?:"
            + "[\\p{Cc}\\p{Cf}]|(?<!\\d)1[3-9]\\d{9}(?!\\d)"
            + "|(?<!\\d)(?:\\d{17}[0-9Xx]|\\d{15})(?!\\d)"
            + "|收件人|手机号|手机号码|身份证|证件号|详细地址|联系电话|家庭住址)");

    private final ShippingOrderRepository orders;
    private final LogisticsCallbackRepository tracking;
    private final FulfillmentStatusChangedOutbox statusOutbox;
    private final TradeClient trade;
    private final Clock clock;

    public ShippingAdminMutationService(ShippingOrderRepository orders, LogisticsCallbackRepository tracking,
                                        FulfillmentStatusChangedOutbox statusOutbox, TradeClient trade,
                                        Clock logisticsClock) {
        this.orders = orders;
        this.tracking = tracking;
        this.statusOutbox = statusOutbox;
        this.trade = trade;
        this.clock = logisticsClock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateLogistics(String shippingNo, UpdateShippingLogisticsCommand command) {
        ShippingOrder order = lock(shippingNo);
        if (command == null || terminal(order.status())) throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        String carrierCode = requiredIdentifier(command.carrierCode(), 32);
        String carrierName = requiredIdentifier(command.carrierName(), 64);
        String waybillNo = requiredIdentifier(command.waybillNo(), 100);
        try {
            if (!orders.updateLogistics(order, carrierCode, carrierName, waybillNo)) {
                throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
            }
        } catch (org.springframework.dao.DuplicateKeyException duplicateWaybill) {
            throw error(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
        }
        if (order.sourceType() == ShippingSourceType.CASH_ORDER) {
            long orderId = positiveOrderId(order.sourceId());
            trade.updateShippingInfo(orderId,
                    new UpdateTradeOrderShippingInfoRequest(carrierName, waybillNo));
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void addTrackingEvent(String shippingNo, AddShippingTrackingEventCommand command) {
        ShippingOrder order = lock(shippingNo);
        if (command == null || command.eventType() == null || terminal(order.status())
                || order.status() == ShippingStatus.FAILED || order.waybillNo() == null
                || order.waybillNo().isBlank()) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime eventTime = command.eventTime() == null ? now
                : command.eventTime().truncatedTo(ChronoUnit.MILLIS);
        if (eventTime.isAfter(now.plusMinutes(5)) || eventTime.isBefore(order.createdAt())) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        String location = optionalSafe(command.locationSummary(), 200);
        String description = requiredSafe(command.description(), 500);
        ShippingStatus target = targetStatus(command.eventType());
        if (rank(target) < rank(order.status())) throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);

        String eventId = eventId(order, command.eventType(), eventTime, location, description);
        boolean inserted = tracking.insertTrackingEvent(new ShippingTrackingEvent(order.id(), eventId,
                order.waybillNo(), command.eventType(), location, description, eventTime, now));
        if (!inserted || rank(target) == rank(order.status())) return;

        ShippingCallbackOrder callbackOrder = new ShippingCallbackOrder(order.id(), order.shippingNo(),
                order.sourceType(), order.sourceId(), order.fulfillmentNo(), order.waybillNo(), order.status(),
                order.deliveredAt(), order.version());
        LocalDateTime deliveredAt = target == ShippingStatus.DELIVERED ? eventTime : order.deliveredAt();
        if (!tracking.advanceOrderStatus(callbackOrder, target, deliveredAt)) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        if (order.sourceType() == ShippingSourceType.LOTTERY_BENEFIT) {
            statusOutbox.append(new FulfillmentStatusChangedEventV1(UUID.randomUUID().toString(),
                    order.fulfillmentNo(), order.shippingNo(), order.sourceType().name(), order.sourceId(),
                    target.name(), now));
        }
    }

    private ShippingOrder lock(String shippingNo) {
        if (shippingNo == null || shippingNo.isBlank() || shippingNo.trim().length() > 64) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        return orders.lockByShippingNo(shippingNo.trim())
                .orElseThrow(() -> error(ShippingErrorCode.SHIPPING_NOT_FOUND));
    }

    private boolean terminal(ShippingStatus status) {
        return status == ShippingStatus.DELIVERED || status == ShippingStatus.TERMINATED;
    }

    private ShippingStatus targetStatus(TrackingEventType eventType) {
        return switch (eventType) {
            case PICKED_UP -> ShippingStatus.SHIPPED;
            case IN_TRANSIT, OUT_FOR_DELIVERY -> ShippingStatus.IN_TRANSIT;
            case DELIVERED -> ShippingStatus.DELIVERED;
        };
    }

    private int rank(ShippingStatus status) {
        return switch (status) {
            case READY -> 0;
            case FULFILLING -> 1;
            case SHIPPED -> 2;
            case IN_TRANSIT -> 3;
            case DELIVERED -> 4;
            case FAILED, TERMINATED -> -1;
        };
    }

    private String eventId(ShippingOrder order, TrackingEventType type, LocalDateTime time,
                          String location, String description) {
        String identity = order.shippingNo() + "|" + order.waybillNo() + "|" + type.name() + "|" + time
                + "|" + (location == null ? "" : location) + "|" + description;
        try {
            return "ADMIN-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private String requiredSafe(String value, int maxLength) {
        String normalized = requiredIdentifier(value, maxLength);
        if (UNSAFE_TRACKING_TEXT.matcher(normalized).find()) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        return normalized;
    }

    private String requiredIdentifier(String value, int maxLength) {
        if (value == null || value.isBlank()) throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        String normalized = value.trim();
        if (normalized.length() > maxLength || normalized.chars().anyMatch(Character::isISOControl)) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        return normalized;
    }

    private String optionalSafe(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength || UNSAFE_TRACKING_TEXT.matcher(normalized).find()) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        return normalized;
    }

    private long positiveOrderId(String sourceId) {
        try {
            long id = Long.parseLong(sourceId);
            if (id > 0) return id;
        } catch (RuntimeException ignored) {
            // A malformed cash-order source is rejected below.
        }
        throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
    }

    private ShippingDomainException error(ShippingErrorCode code) {
        return new ShippingDomainException(code);
    }
}
