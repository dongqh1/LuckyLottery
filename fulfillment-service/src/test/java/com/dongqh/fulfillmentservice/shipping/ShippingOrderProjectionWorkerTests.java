package com.dongqh.fulfillmentservice.shipping;

import com.dongqh.fulfillmentservice.shipping.enums.ShippingSourceType;
import com.dongqh.fulfillmentservice.shipping.enums.ShippingStatus;
import com.dongqh.fulfillmentservice.shipping.persistence.ShippingOrder;
import com.dongqh.fulfillmentservice.shipping.persistence.ShippingOrderRepository;
import com.dongqh.fulfillmentservice.shipping.service.ShippingOrderFulfillmentProjector;
import com.dongqh.fulfillmentservice.shipping.service.ShippingOrderProjectionWorker;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShippingOrderProjectionWorkerTests {

    @Test
    void projectsOnlyEligibleOrderInsideWorkerBoundary() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingOrderFulfillmentProjector projector = mock(ShippingOrderFulfillmentProjector.class);
        when(orders.lockById(12L)).thenReturn(Optional.of(order(ShippingStatus.FULFILLING, "LOGISTICS-12")));

        boolean projected = new ShippingOrderProjectionWorker(orders, projector).projectOne(12L);

        assertThat(projected).isTrue();
        verify(projector).projectFulfillmentState("LOGISTICS-12");
    }

    @Test
    void skipsMissingOrdersTerminalOrdersAndOrdersWithoutFulfillmentTask() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingOrderFulfillmentProjector projector = mock(ShippingOrderFulfillmentProjector.class);
        when(orders.lockById(1L)).thenReturn(Optional.empty());
        when(orders.lockById(2L)).thenReturn(Optional.of(order(ShippingStatus.DELIVERED, "LOGISTICS-2")));
        when(orders.lockById(3L)).thenReturn(Optional.of(order(ShippingStatus.FULFILLING, null)));
        ShippingOrderProjectionWorker worker = new ShippingOrderProjectionWorker(orders, projector);

        assertThat(worker.projectOne(1L)).isFalse();
        assertThat(worker.projectOne(2L)).isFalse();
        assertThat(worker.projectOne(3L)).isFalse();
        verify(projector, never()).projectFulfillmentState(org.mockito.ArgumentMatchers.anyString());
    }

    private static ShippingOrder order(ShippingStatus status, String fulfillmentNo) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 27, 8, 0);
        return new ShippingOrder(12L, "SHIPPING-12", ShippingSourceType.LOTTERY_BENEFIT, "44", 7L, 8L,
                null, "SKU-12", "商品", null, 1, fulfillmentNo, null, null, null, null, status,
                null, null, null, null, null, null, 3, now.minusDays(1), now);
    }
}
