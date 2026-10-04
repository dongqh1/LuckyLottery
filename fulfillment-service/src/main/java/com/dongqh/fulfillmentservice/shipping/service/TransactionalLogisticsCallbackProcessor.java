package com.dongqh.fulfillmentservice.shipping.service;

import com.dongqh.fulfillmentservice.shipping.domain.LogisticsCallbackErrorCode;
import com.dongqh.fulfillmentservice.shipping.domain.LogisticsCallbackException;
import com.dongqh.fulfillmentservice.shipping.enums.ShippingSourceType;
import com.dongqh.fulfillmentservice.shipping.enums.ShippingStatus;
import com.dongqh.fulfillmentservice.shipping.enums.TrackingEventType;
import com.dongqh.fulfillmentservice.shipping.model.LogisticsCallbackCommand;
import com.dongqh.fulfillmentservice.shipping.persistence.LogisticsCallbackRepository;
import com.dongqh.fulfillmentservice.shipping.persistence.ShippingCallbackOrder;
import com.dongqh.fulfillmentservice.shipping.persistence.ShippingCallbackReceipt;
import com.dongqh.fulfillmentservice.shipping.persistence.ShippingTrackingEvent;
import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
public class TransactionalLogisticsCallbackProcessor {
    private static final DateTimeFormatter EVENT_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final LogisticsCallbackRepository repository;
    private final Clock clock;

    public TransactionalLogisticsCallbackProcessor(LogisticsCallbackRepository repository, Clock fulfillmentClock) {
        this.repository = repository;
        this.clock = fulfillmentClock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LogisticsCallbackErrorCode process(LogisticsCallbackCommand command) {
        LocalDateTime receivedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        String nonceDigest = digest(command.nonce());
        String signatureDigest = digest(command.signature());
        ShippingCallbackReceipt receipt = new ShippingCallbackReceipt(
                command.callbackId(), nonceDigest, signatureDigest, command.waybillNo(), command.eventType(),
                databaseTime(command.eventTime()), "RECEIVED", null, receivedAt);
        try {
            repository.insertReceipt(receipt);
        } catch (DuplicateKeyException duplicate) {
            ShippingCallbackReceipt existing = repository.selectReceipt(command.callbackId());
            if (existing != null && sameReceipt(existing, command, nonceDigest, signatureDigest)) {
                return "REJECTED".equals(existing.status())
                        ? LogisticsCallbackErrorCode.fromCode(existing.errorCode()) : null;
            }
            return LogisticsCallbackErrorCode.CALLBACK_REPLAYED;
        }

        ShippingCallbackOrder order = repository.lockOrderByWaybillNo(command.waybillNo());
        if (order == null) {
            reject(receipt.callbackId(), LogisticsCallbackErrorCode.SHIPPING_NOT_FOUND, receivedAt);
            return LogisticsCallbackErrorCode.SHIPPING_NOT_FOUND;
        }
        if (!callbackAllowed(order.status())) {
            reject(receipt.callbackId(), LogisticsCallbackErrorCode.SHIPPING_STATE_CONFLICT, receivedAt);
            return LogisticsCallbackErrorCode.SHIPPING_STATE_CONFLICT;
        }

        insertEvent(order, command, receivedAt);
        advance(order, command.eventType(), receivedAt);
        repository.markReceiptProcessed(receipt.callbackId(), receivedAt);
        return null;
    }

    private void insertEvent(
            ShippingCallbackOrder order, LogisticsCallbackCommand command, LocalDateTime receivedAt
    ) {
        ShippingTrackingEvent event = new ShippingTrackingEvent(
                order.id(), providerEventId(command), command.waybillNo(), command.eventType(),
                normalizeOptional(command.locationSummary()), normalizeOptional(command.description()),
                databaseTime(command.eventTime()), receivedAt);
        try {
            repository.insertTrackingEvent(event);
        } catch (DuplicateKeyException ignored) {
            // A provider event has a deterministic identity, so repeated delivery remains idempotent.
        }
    }

    private void advance(ShippingCallbackOrder order, TrackingEventType eventType, LocalDateTime now) {
        ShippingStatus target = targetStatus(eventType);
        if (rank(target) <= rank(order.status())) {
            return;
        }
        LocalDateTime deliveredAt = target == ShippingStatus.DELIVERED ? now : order.deliveredAt();
        if (!repository.advanceOrderStatus(order, target, deliveredAt)) {
            throw new LogisticsCallbackException(LogisticsCallbackErrorCode.SHIPPING_STATE_CONFLICT);
        }
        if (order.sourceType() == ShippingSourceType.LOTTERY_BENEFIT) {
            repository.appendStatusChanged(new FulfillmentStatusChangedEventV1(
                    UUID.randomUUID().toString(), order.fulfillmentNo(), order.shippingNo(),
                    order.sourceType().name(), order.sourceId(), target.name(), now));
        }
    }

    private void reject(String callbackId, LogisticsCallbackErrorCode code, LocalDateTime processedAt) {
        repository.markReceiptRejected(callbackId, Integer.toString(code.code()), code.message(), processedAt);
    }

    private boolean sameReceipt(
            ShippingCallbackReceipt existing, LogisticsCallbackCommand command,
            String nonceDigest, String signatureDigest
    ) {
        return Objects.equals(existing.nonceDigest(), nonceDigest)
                && Objects.equals(existing.signatureDigest(), signatureDigest)
                && Objects.equals(existing.waybillNo(), command.waybillNo())
                && existing.eventType() == command.eventType()
                && Objects.equals(existing.eventTime(), databaseTime(command.eventTime()));
    }

    private boolean callbackAllowed(ShippingStatus status) {
        return status == ShippingStatus.SHIPPED
                || status == ShippingStatus.IN_TRANSIT
                || status == ShippingStatus.DELIVERED;
    }

    private ShippingStatus targetStatus(TrackingEventType type) {
        return switch (type) {
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

    private String providerEventId(LogisticsCallbackCommand command) {
        LocalDateTime persistedEventTime = databaseTime(command.eventTime());
        return digest(command.waybillNo() + '\n' + command.eventType().name() + '\n'
                + EVENT_TIME_FORMAT.format(persistedEventTime));
    }

    private LocalDateTime databaseTime(LocalDateTime value) {
        return value.truncatedTo(ChronoUnit.MILLIS);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }
}
