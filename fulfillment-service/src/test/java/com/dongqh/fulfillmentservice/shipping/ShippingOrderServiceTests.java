package com.dongqh.fulfillmentservice.shipping;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.domain.model.CreateFulfillmentTaskCommand;
import com.dongqh.fulfillmentservice.domain.model.LogisticsFulfillmentPayload;
import com.dongqh.fulfillmentservice.domain.shipping.ShippingDomainException;
import com.dongqh.fulfillmentservice.domain.shipping.ShippingErrorCode;
import com.dongqh.fulfillmentservice.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillmentservice.enums.shipping.ShippingStatus;
import com.dongqh.fulfillmentservice.domain.model.shipping.CreateShippingOrderCommand;
import com.dongqh.fulfillmentservice.persistence.shipping.ShippingAddressSnapshot;
import com.dongqh.fulfillmentservice.persistence.shipping.ShippingOrder;
import com.dongqh.fulfillmentservice.persistence.shipping.ShippingOrderRepository;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskCreationService;
import com.dongqh.fulfillmentservice.service.shipping.ShippingAddressSnapshotService;
import com.dongqh.fulfillmentservice.service.shipping.ShippingOrderService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShippingOrderServiceTests {

    @Test
    void createsFulfillmentTaskWithMaskedAddressAndReturnsOrder() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingAddressSnapshotService snapshots = mock(ShippingAddressSnapshotService.class);
        FulfillmentTaskCreationService tasks = mock(FulfillmentTaskCreationService.class);
        ShippingAddressSnapshot snapshot = snapshot();
        when(snapshots.require(3L)).thenReturn(snapshot);
        when(orders.findBySource(ShippingSourceType.CASH_ORDER, "order-1")).thenReturn(Optional.empty());
        when(orders.insert(any())).thenAnswer(invocation ->
                invocation.<ShippingOrder>getArgument(0).withId(44L));
        when(orders.markFulfilling(44L, "LOGISTICS-44")).thenReturn(true);
        when(orders.findById(44L)).thenReturn(Optional.of(order(44L, "LOGISTICS-44")));

        var result = new ShippingOrderService(orders, snapshots, tasks)
                .create(command());

        assertThat(result.shippingNo()).isEqualTo("SHIPPING-44");
        assertThat(result.status()).isEqualTo(ShippingStatus.FULFILLING);
        verify(tasks).create(any(CreateFulfillmentTaskCommand.class));
        org.mockito.ArgumentCaptor<CreateFulfillmentTaskCommand> captor =
                org.mockito.ArgumentCaptor.forClass(CreateFulfillmentTaskCommand.class);
        verify(tasks).create(captor.capture());
        assertThat(captor.getValue().fulfillmentType()).isEqualTo(FulfillmentType.LOGISTICS);
        assertThat(captor.getValue().payload()).isEqualTo(
                new LogisticsFulfillmentPayload(44L, "SKU-1", 2, "张*", "138****8000",
                        LogisticsFulfillmentPayload.REDACTED_REGION));
    }

    @Test
    void doesNotCreateAnotherTaskForAnIdenticalSourceRequest() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingAddressSnapshotService snapshots = mock(ShippingAddressSnapshotService.class);
        FulfillmentTaskCreationService tasks = mock(FulfillmentTaskCreationService.class);
        when(snapshots.require(3L)).thenReturn(snapshot());
        when(orders.findBySource(ShippingSourceType.CASH_ORDER, "order-1"))
                .thenReturn(Optional.of(order(44L, "LOGISTICS-44")));

        var result = new ShippingOrderService(orders, snapshots, tasks).create(command());

        assertThat(result.shippingNo()).isEqualTo("SHIPPING-44");
        org.mockito.Mockito.verifyNoInteractions(tasks);
    }

    @Test
    void rejectsSourceIdentityReuseWithDifferentOrderData() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingAddressSnapshotService snapshots = mock(ShippingAddressSnapshotService.class);
        FulfillmentTaskCreationService tasks = mock(FulfillmentTaskCreationService.class);
        ShippingOrder existing = order(44L, "LOGISTICS-44");
        when(snapshots.require(3L)).thenReturn(snapshot());
        when(orders.findBySource(ShippingSourceType.CASH_ORDER, "order-1")).thenReturn(Optional.of(existing));
        CreateShippingOrderCommand changed = new CreateShippingOrderCommand(
                ShippingSourceType.CASH_ORDER, "order-1", 7L, 3L, null, "SKU-CHANGED", "商品", null, 2, null);

        assertThatThrownBy(() -> new ShippingOrderService(orders, snapshots, tasks).create(changed))
                .isInstanceOf(ShippingDomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
    }

    @Test
    void userCanReadOnlyTheirOwnShippingOrderAndAddressFieldsRemainMasked() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingAddressSnapshotService snapshots = mock(ShippingAddressSnapshotService.class);
        when(orders.findByShippingNo("SHIPPING-44")).thenReturn(Optional.of(order(44L, "LOGISTICS-44")));
        when(snapshots.require(3L)).thenReturn(snapshot());
        ShippingOrderService service = new ShippingOrderService(orders, snapshots, mock(FulfillmentTaskCreationService.class));

        var result = service.getForUser(7L, " SHIPPING-44 ");

        assertThat(result.receiverMasked()).isEqualTo("张*");
        assertThat(result.phoneMasked()).isEqualTo("138****8000");
        assertThat(result.regionMasked()).isEqualTo("浙江省***");
        assertThat(result.regionMasked()).doesNotContain("杭州市");
        assertThatThrownBy(() -> service.getForUser(8L, "SHIPPING-44"))
                .isInstanceOf(ShippingDomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", ShippingErrorCode.SHIPPING_NOT_FOUND);
    }

    private static CreateShippingOrderCommand command() {
        return new CreateShippingOrderCommand(ShippingSourceType.CASH_ORDER, "order-1", 7L, 3L,
                null, "SKU-1", "商品", null, 2, null);
    }

    private static ShippingAddressSnapshot snapshot() {
        return new ShippingAddressSnapshot(3L, "ADDRESS-3", 7L, 9L, ShippingSourceType.CASH_ORDER, "order-1",
                "cipher-1", "cipher-2", "cipher-3", "cipher-4", "cipher-5", "cipher-6",
                "张*", "138****8000", "浙江省***", LocalDateTime.now());
    }

    private static ShippingOrder order(long id, String fulfillmentNo) {
        return new ShippingOrder(id, "SHIPPING-" + id, ShippingSourceType.CASH_ORDER, "order-1", 7L, 3L,
                null, "SKU-1", "商品", null, 2, fulfillmentNo, null, null, null, null, ShippingStatus.FULFILLING,
                null, null, null, null, null, null, 0, LocalDateTime.now(), LocalDateTime.now());
    }
}
