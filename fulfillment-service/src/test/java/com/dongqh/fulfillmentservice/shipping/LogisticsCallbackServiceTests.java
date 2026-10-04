package com.dongqh.fulfillmentservice.shipping;

import com.dongqh.fulfillmentservice.shipping.config.LogisticsCallbackProperties;
import com.dongqh.fulfillmentservice.shipping.crypto.LogisticsCallbackSigner;
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
import com.dongqh.fulfillmentservice.shipping.service.LogisticsCallbackService;
import com.dongqh.fulfillmentservice.shipping.service.TransactionalLogisticsCallbackProcessor;
import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogisticsCallbackServiceTests {
    private static final String SECRET = "test-callback-secret-32-bytes-long";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T04:00:00Z"), ZoneOffset.UTC);

    @Test
    void acceptedReceiptIsIdempotentAndTrackingStatusNeverRegresses() {
        FakeRepository repository = new FakeRepository(order(ShippingSourceType.CASH_ORDER));
        LogisticsCallbackSigner signer = new LogisticsCallbackSigner(SECRET);
        LogisticsCallbackService service = service(repository, signer);
        LocalDateTime eventTime = LocalDateTime.parse("2026-09-27T03:59:00.123");
        LogisticsCallbackCommand first = signed(signer, "callback-1", "nonce-1", "WAYBILL-1",
                TrackingEventType.IN_TRANSIT, eventTime);

        service.handle(first);
        service.handle(first);
        service.handle(signed(signer, "callback-2", "nonce-2", "WAYBILL-1",
                TrackingEventType.IN_TRANSIT, eventTime));
        service.handle(signed(signer, "callback-3", "nonce-3", "WAYBILL-1",
                TrackingEventType.DELIVERED, eventTime.plusMinutes(1)));
        service.handle(signed(signer, "callback-4", "nonce-4", "WAYBILL-1",
                TrackingEventType.IN_TRANSIT, eventTime.minusHours(1)));

        assertThat(repository.order.status()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(repository.order.deliveredAt()).isEqualTo(LocalDateTime.parse("2026-09-27T04:00:00"));
        assertThat(repository.trackingEvents).hasSize(3);
        assertThat(repository.receipts).hasSize(4);
        assertThat(repository.receipts.get("callback-1").status()).isEqualTo("PROCESSED");
    }

    @Test
    void trackingDedupeUsesTheSameMillisecondPrecisionAsPersistedEventTime() {
        FakeRepository repository = new FakeRepository(order(ShippingSourceType.CASH_ORDER));
        LogisticsCallbackSigner signer = new LogisticsCallbackSigner(SECRET);
        LogisticsCallbackService service = service(repository, signer);
        LogisticsCallbackCommand first = signed(signer, "callback-1", "nonce-1", "WAYBILL-1",
                TrackingEventType.IN_TRANSIT, LocalDateTime.parse("2026-09-27T03:59:00.123456789"));
        LogisticsCallbackCommand sameDatabaseTime = signed(signer, "callback-2", "nonce-2", "WAYBILL-1",
                TrackingEventType.IN_TRANSIT, LocalDateTime.parse("2026-09-27T03:59:00.123999999"));

        service.handle(first);
        service.handle(sameDatabaseTime);

        assertThat(repository.trackingEvents).singleElement()
                .extracting(ShippingTrackingEvent::eventTime)
                .isEqualTo(LocalDateTime.parse("2026-09-27T03:59:00.123"));
        assertThat(repository.receipts).hasSize(2);
    }

    @Test
    void lotteryShippingWritesOnlyAStatusOutboxEventWhenStatusAdvances() {
        FakeRepository repository = new FakeRepository(order(ShippingSourceType.LOTTERY_BENEFIT));
        LogisticsCallbackSigner signer = new LogisticsCallbackSigner(SECRET);
        LogisticsCallbackService service = service(repository, signer);

        service.handle(signed(signer, "callback-1", "nonce-1", "WAYBILL-1",
                TrackingEventType.DELIVERED, LocalDateTime.parse("2026-09-27T03:59:00")));

        assertThat(repository.order.status()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(repository.statusEvents).singleElement().satisfies(event -> {
            assertThat(event.sourceType()).isEqualTo(ShippingSourceType.LOTTERY_BENEFIT.name());
            assertThat(event.sourceId()).isEqualTo("BENEFIT-1");
            assertThat(event.status()).isEqualTo(ShippingStatus.DELIVERED.name());
            assertThat(event.eventId()).isNotBlank();
        });
    }

    @Test
    void rejectedCallbacksRemainAuditedAndRepeatTheOriginalSafeError() {
        FakeRepository repository = new FakeRepository(null);
        LogisticsCallbackSigner signer = new LogisticsCallbackSigner(SECRET);
        LogisticsCallbackService service = service(repository, signer);
        LogisticsCallbackCommand missing = signed(signer, "callback-1", "nonce-1", "UNKNOWN-WAYBILL",
                TrackingEventType.PICKED_UP, LocalDateTime.parse("2026-09-27T03:59:00"));

        assertError(service, missing, LogisticsCallbackErrorCode.SHIPPING_NOT_FOUND);
        assertError(service, missing, LogisticsCallbackErrorCode.SHIPPING_NOT_FOUND);
        assertThat(repository.receipts.get("callback-1").status()).isEqualTo("REJECTED");
        assertThat(repository.receipts.get("callback-1").errorCode())
                .isEqualTo(Integer.toString(LogisticsCallbackErrorCode.SHIPPING_NOT_FOUND.code()));
    }

    @Test
    void callbackIdentifierOrNonceReuseWithDifferentPayloadIsRejectedAsReplay() {
        FakeRepository repository = new FakeRepository(order(ShippingSourceType.CASH_ORDER));
        LogisticsCallbackSigner signer = new LogisticsCallbackSigner(SECRET);
        LogisticsCallbackService service = service(repository, signer);
        service.handle(signed(signer, "callback-1", "nonce-1", "WAYBILL-1",
                TrackingEventType.IN_TRANSIT, LocalDateTime.parse("2026-09-27T03:59:00")));

        assertError(service, signed(signer, "callback-1", "nonce-2", "WAYBILL-1",
                TrackingEventType.DELIVERED, LocalDateTime.parse("2026-09-27T03:59:30")),
                LogisticsCallbackErrorCode.CALLBACK_REPLAYED);
        assertError(service, signed(signer, "callback-2", "nonce-1", "WAYBILL-1",
                TrackingEventType.DELIVERED, LocalDateTime.parse("2026-09-27T03:59:30")),
                LogisticsCallbackErrorCode.CALLBACK_REPLAYED);
        assertThat(repository.receipts).hasSize(1);
    }

    @Test
    void rejectsDelimiterInjectionInvalidSignatureAndExpiredTimestampBeforePersistence() {
        FakeRepository repository = new FakeRepository(order(ShippingSourceType.CASH_ORDER));
        LogisticsCallbackSigner signer = new LogisticsCallbackSigner(SECRET);
        LogisticsCallbackService service = service(repository, signer);
        LogisticsCallbackCommand delimiterInjection = signed(signer, "part-a\npart-b", "part-c", "WAYBILL-1",
                TrackingEventType.IN_TRANSIT, LocalDateTime.parse("2026-09-27T03:59:00"));
        LogisticsCallbackCommand valid = signed(signer, "callback-2", "nonce-2", "WAYBILL-1",
                TrackingEventType.IN_TRANSIT, LocalDateTime.parse("2026-09-27T03:59:00"));
        LogisticsCallbackCommand tampered = new LogisticsCallbackCommand(
                valid.callbackId(), valid.nonce(), valid.timestampEpochSecond(), valid.waybillNo(),
                TrackingEventType.DELIVERED, valid.eventTime(), valid.locationSummary(),
                valid.description(), valid.signature());
        LogisticsCallbackCommand expired = signedAt(signer, "callback-3", "nonce-3", 301);

        assertError(service, delimiterInjection, LogisticsCallbackErrorCode.SHIPPING_REQUEST_INVALID);
        assertError(service, tampered, LogisticsCallbackErrorCode.CALLBACK_SIGNATURE_INVALID);
        assertError(service, expired, LogisticsCallbackErrorCode.CALLBACK_SIGNATURE_INVALID);
        assertThat(repository.receipts).isEmpty();
    }

    @Test
    void terminalShippingStateIsAuditedAsARepeatableConflict() {
        FakeRepository repository = new FakeRepository(order(ShippingSourceType.CASH_ORDER,
                ShippingStatus.FAILED));
        LogisticsCallbackSigner signer = new LogisticsCallbackSigner(SECRET);
        LogisticsCallbackService service = service(repository, signer);
        LogisticsCallbackCommand command = signed(signer, "callback-1", "nonce-1", "WAYBILL-1",
                TrackingEventType.IN_TRANSIT, LocalDateTime.parse("2026-09-27T03:59:00"));

        assertError(service, command, LogisticsCallbackErrorCode.SHIPPING_STATE_CONFLICT);
        assertError(service, command, LogisticsCallbackErrorCode.SHIPPING_STATE_CONFLICT);
        assertThat(repository.trackingEvents).isEmpty();
        assertThat(repository.receipts.get("callback-1").status()).isEqualTo("REJECTED");
    }

    private LogisticsCallbackService service(FakeRepository repository, LogisticsCallbackSigner signer) {
        LogisticsCallbackProperties properties = new LogisticsCallbackProperties(SECRET,
                java.time.Duration.ofMinutes(5));
        TransactionalLogisticsCallbackProcessor processor =
                new TransactionalLogisticsCallbackProcessor(repository, CLOCK);
        return new LogisticsCallbackService(signer, properties, processor, CLOCK);
    }

    private static ShippingCallbackOrder order(ShippingSourceType sourceType) {
        return order(sourceType, ShippingStatus.SHIPPED);
    }

    private static ShippingCallbackOrder order(ShippingSourceType sourceType, ShippingStatus status) {
        return new ShippingCallbackOrder(1, "SHIP-1", sourceType, "BENEFIT-1", "FULFILL-1", "WAYBILL-1",
                status, null, 0);
    }

    private static LogisticsCallbackCommand signed(
            LogisticsCallbackSigner signer, String callbackId, String nonce, String waybillNo,
            TrackingEventType eventType, LocalDateTime eventTime
    ) {
        LogisticsCallbackCommand unsigned = new LogisticsCallbackCommand(callbackId, nonce,
                CLOCK.instant().getEpochSecond(), waybillNo, eventType, eventTime,
                " 杭州中转站 ", " 运输中 ", null);
        return withSignature(signer, unsigned);
    }

    private static LogisticsCallbackCommand signedAt(
            LogisticsCallbackSigner signer, String callbackId, String nonce, long offsetSeconds
    ) {
        LogisticsCallbackCommand unsigned = new LogisticsCallbackCommand(callbackId, nonce,
                CLOCK.instant().getEpochSecond() + offsetSeconds, "WAYBILL-1", TrackingEventType.PICKED_UP,
                LocalDateTime.parse("2026-09-27T03:59:00"), null, null, null);
        return withSignature(signer, unsigned);
    }

    private static LogisticsCallbackCommand withSignature(
            LogisticsCallbackSigner signer, LogisticsCallbackCommand command
    ) {
        return new LogisticsCallbackCommand(command.callbackId(), command.nonce(), command.timestampEpochSecond(),
                command.waybillNo(), command.eventType(), command.eventTime(), command.locationSummary(),
                command.description(), signer.sign(command));
    }

    private static void assertError(
            LogisticsCallbackService service, LogisticsCallbackCommand command, LogisticsCallbackErrorCode expected
    ) {
        assertThatThrownBy(() -> service.handle(command))
                .isInstanceOfSatisfying(LogisticsCallbackException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }

    private static final class FakeRepository implements LogisticsCallbackRepository {
        private ShippingCallbackOrder order;
        private final Map<String, ShippingCallbackReceipt> receipts = new HashMap<>();
        private final Map<String, String> callbackByNonceDigest = new HashMap<>();
        private final Set<String> providerEventIds = new HashSet<>();
        private final List<ShippingTrackingEvent> trackingEvents = new ArrayList<>();
        private final List<FulfillmentStatusChangedEventV1> statusEvents = new ArrayList<>();

        private FakeRepository(ShippingCallbackOrder order) {
            this.order = order;
        }

        @Override
        public void insertReceipt(ShippingCallbackReceipt receipt) {
            if (receipts.containsKey(receipt.callbackId())
                    || callbackByNonceDigest.containsKey(receipt.nonceDigest())) {
                throw new DuplicateKeyException("duplicate callback receipt");
            }
            receipts.put(receipt.callbackId(), receipt);
            callbackByNonceDigest.put(receipt.nonceDigest(), receipt.callbackId());
        }

        @Override
        public ShippingCallbackReceipt selectReceipt(String callbackId) {
            return receipts.get(callbackId);
        }

        @Override
        public ShippingCallbackOrder lockOrderByWaybillNo(String waybillNo) {
            return order != null && order.waybillNo().equals(waybillNo) ? order : null;
        }

        @Override
        public boolean insertTrackingEvent(ShippingTrackingEvent event) {
            if (!providerEventIds.add(event.providerEventId())) {
                return false;
            }
            trackingEvents.add(event);
            return true;
        }

        @Override
        public List<ShippingTrackingEvent> findTrackingByShippingOrderId(long shippingOrderId) {
            return trackingEvents.stream()
                    .filter(event -> event.shippingOrderId() == shippingOrderId)
                    .toList();
        }

        @Override
        public boolean advanceOrderStatus(
                ShippingCallbackOrder expected, ShippingStatus target, LocalDateTime deliveredAt
        ) {
            if (order.version() != expected.version() || order.status() != expected.status()) {
                return false;
            }
            order = new ShippingCallbackOrder(order.id(), order.shippingNo(), order.sourceType(), order.sourceId(),
                    order.fulfillmentNo(), order.waybillNo(), target, deliveredAt, order.version() + 1);
            return true;
        }

        @Override
        public void markReceiptProcessed(String callbackId, LocalDateTime processedAt) {
            replaceReceipt(callbackId, "PROCESSED", null);
        }

        @Override
        public void markReceiptRejected(
                String callbackId, String errorCode, String errorMessage, LocalDateTime processedAt
        ) {
            replaceReceipt(callbackId, "REJECTED", errorCode);
        }

        @Override
        public void appendStatusChanged(FulfillmentStatusChangedEventV1 event) {
            statusEvents.add(event);
        }

        private void replaceReceipt(String callbackId, String status, String errorCode) {
            ShippingCallbackReceipt old = receipts.get(callbackId);
            receipts.put(callbackId, new ShippingCallbackReceipt(old.callbackId(), old.nonceDigest(),
                    old.signatureDigest(), old.waybillNo(), old.eventType(), old.eventTime(), status, errorCode,
                    old.receivedAt()));
        }
    }
}
