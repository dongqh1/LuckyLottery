package com.dongqh.fulfillment.shipping;

import com.dongqh.fulfillment.domain.model.shipping.UpdateShippingLogisticsCommand;
import com.dongqh.fulfillment.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillment.enums.shipping.ShippingStatus;
import com.dongqh.fulfillment.persistence.shipping.LogisticsCallbackRepository;
import com.dongqh.fulfillment.persistence.shipping.ShippingOrder;
import com.dongqh.fulfillment.persistence.shipping.ShippingOrderRepository;
import com.dongqh.fulfillment.service.port.FulfillmentStatusChangedOutbox;
import com.dongqh.fulfillment.service.shipping.ShippingAdminMutationService;
import com.dongqh.hmapi.client.TradeClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShippingAdminMutationServiceTests {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T04:00:00Z"), ZoneOffset.ofHours(8));

    @Test
    void acceptsNumericWaybillThatLooksLikeAPhoneNumber() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingOrder order = new ShippingOrder(12L, "SHIPPING-12", ShippingSourceType.LOTTERY_BENEFIT,
                "44", 7L, 8L, null, "SKU-12", "实物奖品", null, 1, "LOGISTICS-12", null,
                null, null, null, ShippingStatus.READY, null, null, null, null, null, null,
                3, NOW.minusDays(1), NOW.minusHours(1));
        when(orders.lockByShippingNo("SHIPPING-12")).thenReturn(Optional.of(order));
        when(orders.updateLogistics(eq(order), eq("YTO"), eq("圆通速递"), eq("13800138000"))).thenReturn(true);

        ShippingAdminMutationService service = new ShippingAdminMutationService(orders,
                mock(LogisticsCallbackRepository.class), mock(FulfillmentStatusChangedOutbox.class),
                mock(TradeClient.class), CLOCK);

        service.updateLogistics("SHIPPING-12",
                new UpdateShippingLogisticsCommand("YTO", "圆通速递", "13800138000"));

        verify(orders).updateLogistics(order, "YTO", "圆通速递", "13800138000");
    }
}
