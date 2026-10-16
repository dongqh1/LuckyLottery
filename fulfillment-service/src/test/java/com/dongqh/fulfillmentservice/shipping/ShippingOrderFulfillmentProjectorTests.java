package com.dongqh.fulfillmentservice.shipping;

import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskView;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskReadService;
import com.dongqh.fulfillmentservice.service.port.FulfillmentStatusChangedOutbox;
import com.dongqh.fulfillmentservice.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillmentservice.enums.shipping.ShippingStatus;
import com.dongqh.fulfillmentservice.persistence.shipping.ShippingOrder;
import com.dongqh.fulfillmentservice.persistence.shipping.ShippingOrderRepository;
import com.dongqh.fulfillmentservice.service.shipping.ShippingOrderFulfillmentProjector;
import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShippingOrderFulfillmentProjectorTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 27, 8, 0, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T00:00:00Z"), ZoneOffset.ofHours(8));

    @Test
    void successfulLogisticsTaskProjectsShippingAndAppendsLotteryEventAfterCas() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        FulfillmentTaskReadService tasks = mock(FulfillmentTaskReadService.class);
        FulfillmentStatusChangedOutbox outbox = mock(FulfillmentStatusChangedOutbox.class);
        ShippingOrder before = order(ShippingSourceType.LOTTERY_BENEFIT, ShippingStatus.FULFILLING);
        when(orders.findByFulfillmentNo("LOGISTICS-12")).thenReturn(Optional.of(before));
        when(tasks.get("LOGISTICS-12")).thenReturn(task(
                FulfillmentStatus.SUCCEEDED, "WB-900", null, null));
        when(orders.updateProjection(eq(before), any(ShippingOrder.class))).thenReturn(true);

        projector(orders, tasks, outbox).projectFulfillmentState("LOGISTICS-12");

        ArgumentCaptor<ShippingOrder> projected = ArgumentCaptor.forClass(ShippingOrder.class);
        verify(orders).updateProjection(eq(before), projected.capture());
        assertThat(projected.getValue().status()).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(projected.getValue().carrierCode()).isEqualTo("SIMULATOR");
        assertThat(projected.getValue().carrierName()).isEqualTo("模拟物流");
        assertThat(projected.getValue().waybillNo()).isEqualTo("WB-900");
        assertThat(projected.getValue().shippedAt()).isEqualTo(NOW);

        ArgumentCaptor<FulfillmentStatusChangedEventV1> event =
                ArgumentCaptor.forClass(FulfillmentStatusChangedEventV1.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventId()).isNotBlank();
        assertThat(event.getValue().fulfillmentNo()).isEqualTo("LOGISTICS-12");
        assertThat(event.getValue().shippingNo()).isEqualTo("SHIPPING-12");
        assertThat(event.getValue().sourceType()).isEqualTo("LOTTERY_BENEFIT");
        assertThat(event.getValue().sourceId()).isEqualTo("44");
        assertThat(event.getValue().status()).isEqualTo("SHIPPED");
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void quarantinedTaskProjectsFailedAndUsesSafeFallbacks() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        FulfillmentTaskReadService tasks = mock(FulfillmentTaskReadService.class);
        FulfillmentStatusChangedOutbox outbox = mock(FulfillmentStatusChangedOutbox.class);
        ShippingOrder before = order(ShippingSourceType.LOTTERY_BENEFIT, ShippingStatus.FULFILLING);
        when(orders.findByFulfillmentNo("LOGISTICS-12")).thenReturn(Optional.of(before));
        when(tasks.get("LOGISTICS-12")).thenReturn(task(FulfillmentStatus.QUARANTINED, null, null, null));
        when(orders.updateProjection(eq(before), any(ShippingOrder.class))).thenReturn(true);

        projector(orders, tasks, outbox).projectFulfillmentState("LOGISTICS-12");

        ArgumentCaptor<ShippingOrder> projected = ArgumentCaptor.forClass(ShippingOrder.class);
        verify(orders).updateProjection(eq(before), projected.capture());
        assertThat(projected.getValue().status()).isEqualTo(ShippingStatus.FAILED);
        assertThat(projected.getValue().lastErrorCode()).isEqualTo("FULFILLMENT_QUARANTINED");
        assertThat(projected.getValue().lastErrorMessage()).isEqualTo("物流履约已安全隔离");
        assertThat(projected.getValue().failedAt()).isEqualTo(NOW);
        verify(outbox).append(any(FulfillmentStatusChangedEventV1.class));
    }

    @Test
    void retryingTaskResetsFailedShippingOrderToFulfilling() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        FulfillmentTaskReadService tasks = mock(FulfillmentTaskReadService.class);
        FulfillmentStatusChangedOutbox outbox = mock(FulfillmentStatusChangedOutbox.class);
        ShippingOrder before = order(ShippingSourceType.LOTTERY_BENEFIT, ShippingStatus.FAILED);
        when(orders.findByFulfillmentNo("LOGISTICS-12")).thenReturn(Optional.of(before));
        when(tasks.get("LOGISTICS-12")).thenReturn(task(FulfillmentStatus.RETRY_WAITING, null, null, null));
        when(orders.updateProjection(eq(before), any(ShippingOrder.class))).thenReturn(true);

        projector(orders, tasks, outbox).projectFulfillmentState("LOGISTICS-12");

        ArgumentCaptor<ShippingOrder> projected = ArgumentCaptor.forClass(ShippingOrder.class);
        verify(orders).updateProjection(eq(before), projected.capture());
        assertThat(projected.getValue().status()).isEqualTo(ShippingStatus.FULFILLING);
        assertThat(projected.getValue().lastErrorCode()).isNull();
        assertThat(projected.getValue().lastErrorMessage()).isNull();
        assertThat(projected.getValue().failedAt()).isNull();
        verify(outbox).append(any(FulfillmentStatusChangedEventV1.class));
    }

    @Test
    void terminatedTaskProjectsTerminalStateAndEmitsEvent() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        FulfillmentTaskReadService tasks = mock(FulfillmentTaskReadService.class);
        FulfillmentStatusChangedOutbox outbox = mock(FulfillmentStatusChangedOutbox.class);
        ShippingOrder before = order(ShippingSourceType.LOTTERY_BENEFIT, ShippingStatus.FAILED);
        when(orders.findByFulfillmentNo("LOGISTICS-12")).thenReturn(Optional.of(before));
        when(tasks.get("LOGISTICS-12")).thenReturn(task(
                FulfillmentStatus.TERMINATED, null, "CUSTOM_TERMINATION", "任务已安全终止"));
        when(orders.updateProjection(eq(before), any(ShippingOrder.class))).thenReturn(true);

        projector(orders, tasks, outbox).projectFulfillmentState("LOGISTICS-12");

        ArgumentCaptor<ShippingOrder> projected = ArgumentCaptor.forClass(ShippingOrder.class);
        verify(orders).updateProjection(eq(before), projected.capture());
        assertThat(projected.getValue().status()).isEqualTo(ShippingStatus.TERMINATED);
        assertThat(projected.getValue().lastErrorCode()).isEqualTo("CUSTOM_TERMINATION");
        assertThat(projected.getValue().lastErrorMessage()).isEqualTo("任务已安全终止");
        assertThat(projected.getValue().terminatedAt()).isEqualTo(NOW);
        verify(outbox).append(any(FulfillmentStatusChangedEventV1.class));
    }

    @Test
    void failedCasDoesNotAppendStatusEvent() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        FulfillmentTaskReadService tasks = mock(FulfillmentTaskReadService.class);
        FulfillmentStatusChangedOutbox outbox = mock(FulfillmentStatusChangedOutbox.class);
        ShippingOrder before = order(ShippingSourceType.LOTTERY_BENEFIT, ShippingStatus.FULFILLING);
        when(orders.findByFulfillmentNo("LOGISTICS-12")).thenReturn(Optional.of(before));
        when(tasks.get("LOGISTICS-12")).thenReturn(task(FulfillmentStatus.SUCCEEDED, "WB-900", null, null));
        when(orders.updateProjection(eq(before), any(ShippingOrder.class))).thenReturn(false);

        projector(orders, tasks, outbox).projectFulfillmentState("LOGISTICS-12");

        verify(outbox, never()).append(any(FulfillmentStatusChangedEventV1.class));
    }

    @Test
    void nonLotterySourceDoesNotEmitLotteryStatusEvent() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        FulfillmentTaskReadService tasks = mock(FulfillmentTaskReadService.class);
        FulfillmentStatusChangedOutbox outbox = mock(FulfillmentStatusChangedOutbox.class);
        ShippingOrder before = order(ShippingSourceType.CASH_ORDER, ShippingStatus.FULFILLING);
        when(orders.findByFulfillmentNo("LOGISTICS-12")).thenReturn(Optional.of(before));
        when(tasks.get("LOGISTICS-12")).thenReturn(task(
                FulfillmentStatus.SUCCEEDED, "WB-900", null, null, "CASH_ORDER"));
        when(orders.updateProjection(eq(before), any(ShippingOrder.class))).thenReturn(true);

        projector(orders, tasks, outbox).projectFulfillmentState("LOGISTICS-12");

        verify(outbox, never()).append(any(FulfillmentStatusChangedEventV1.class));
    }

    @Test
    void terminalShippingOrderIsNotReprojected() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        FulfillmentTaskReadService tasks = mock(FulfillmentTaskReadService.class);
        FulfillmentStatusChangedOutbox outbox = mock(FulfillmentStatusChangedOutbox.class);
        ShippingOrder before = order(ShippingSourceType.LOTTERY_BENEFIT, ShippingStatus.DELIVERED);
        when(orders.findByFulfillmentNo("LOGISTICS-12")).thenReturn(Optional.of(before));

        projector(orders, tasks, outbox).projectFulfillmentState("LOGISTICS-12");

        verify(tasks, never()).get(any());
        verify(orders, never()).updateProjection(any(), any());
        verify(outbox, never()).append(any());
    }

    private static ShippingOrderFulfillmentProjector projector(
            ShippingOrderRepository orders,
            FulfillmentTaskReadService tasks,
            FulfillmentStatusChangedOutbox outbox
    ) {
        return new ShippingOrderFulfillmentProjector(orders, tasks, outbox, CLOCK);
    }

    private static ShippingOrder order(ShippingSourceType source, ShippingStatus status) {
        return new ShippingOrder(12L, "SHIPPING-12", source, "44", 7L, 8L, null, "SKU-12", "商品", null,
                1, "LOGISTICS-12", null, null, null, null, status, "OLD_ERROR", "old error",
                null, null, status == ShippingStatus.FAILED ? NOW.minusMinutes(5) : null,
                null, 3, NOW.minusDays(1), NOW.minusHours(1));
    }

    private static FulfillmentTaskView task(
            FulfillmentStatus status,
            String externalReference,
            String errorCode,
            String errorMessage
    ) {
        return task(status, externalReference, errorCode, errorMessage, "LOTTERY_BENEFIT");
    }

    private static FulfillmentTaskView task(
            FulfillmentStatus status,
            String externalReference,
            String errorCode,
            String errorMessage,
            String sourceType
    ) {
        return new FulfillmentTaskView(91L, "LOGISTICS-12", sourceType, "44",
                FulfillmentType.LOGISTICS, 7L, null, "fingerprint", status, 1, 5, null,
                externalReference, null, errorCode, errorMessage, NOW, NOW.minusMinutes(1), NOW);
    }
}
