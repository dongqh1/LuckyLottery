package com.dongqh.fulfillment.shipping;

import com.dongqh.fulfillment.domain.model.shipping.CreateShippingOrderFromSnapshotCommand;
import com.dongqh.fulfillment.domain.model.shipping.ShippingAddressDetails;
import com.dongqh.fulfillment.service.shipping.ShippingOrderService;
import com.dongqh.fulfillment.service.shipping.TradeOrderShippingService;
import com.dongqh.hmapi.client.TradeClient;
import com.dongqh.hmapi.dto.OrderPaidMessage;
import com.dongqh.hmapi.dto.TradeOrderShippingDetailsDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeOrderShippingServiceTests {
    @Test
    void createsOneCashOrderShipmentFromTheFrozenTradeAddress() {
        TradeClient trade = mock(TradeClient.class);
        ShippingOrderService shipping = mock(ShippingOrderService.class);
        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 21, 12, 30);
        when(trade.getShippingDetails(101L)).thenReturn(new TradeOrderShippingDetailsDTO(101L, 7L, paidAt,
                "林女士", "13800001111", "浙江省", "杭州市", "西湖区", "文三路 1 号",
                "TRADE-ORDER-101", "茶杯 × 2、茶壶 × 1", "cup.png", 3));
        OrderPaidMessage event = new OrderPaidMessage(UUID.randomUUID(), 101L, 7L, paidAt);

        new TradeOrderShippingService(trade, shipping).createForPaidOrder(event);

        ArgumentCaptor<CreateShippingOrderFromSnapshotCommand> command =
                ArgumentCaptor.forClass(CreateShippingOrderFromSnapshotCommand.class);
        ArgumentCaptor<ShippingAddressDetails> address = ArgumentCaptor.forClass(ShippingAddressDetails.class);
        verify(shipping).createFromFrozenAddress(command.capture(), address.capture());
        assertThat(command.getValue()).isEqualTo(new CreateShippingOrderFromSnapshotCommand(
                "101", 7L, "TRADE-ORDER-101", "茶杯 × 2、茶壶 × 1", "cup.png", 3));
        assertThat(address.getValue()).isEqualTo(new ShippingAddressDetails(null, 7L, "浙江省", "杭州市",
                "西湖区", "13800001111", "文三路 1 号", "林女士"));
    }
}
