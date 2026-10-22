package com.dongqh.fulfillmentservice.service.shipping;

import com.dongqh.fulfillmentservice.domain.model.shipping.CreateShippingOrderFromSnapshotCommand;
import com.dongqh.fulfillmentservice.domain.model.shipping.ShippingAddressDetails;
import com.dongqh.fulfillmentservice.domain.model.shipping.ShippingOrderView;
import com.dongqh.fulfillmentservice.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillmentservice.domain.shipping.ShippingDomainException;
import com.dongqh.fulfillmentservice.domain.shipping.ShippingErrorCode;
import com.dongqh.fulfillmentservice.service.shipping.ShippingOrderService;
import com.dongqh.hmapi.client.TradeClient;
import com.dongqh.hmapi.dto.OrderPaidMessage;
import com.dongqh.hmapi.dto.TradeOrderShippingDetailsDTO;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class TradeOrderShippingService {
    private final TradeClient trade;
    private final ShippingOrderService shipping;

    public TradeOrderShippingService(TradeClient trade, ShippingOrderService shipping) {
        this.trade = trade;
        this.shipping = shipping;
    }

    public ShippingOrderView createForPaidOrder(OrderPaidMessage event) {
        if (event == null || event.orderId() == null || event.orderId() <= 0
                || event.userId() == null || event.userId() <= 0 || event.paidAt() == null) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        TradeOrderShippingDetailsDTO details = trade.getShippingDetails(event.orderId());
        if (details == null || !Objects.equals(details.orderId(), event.orderId())
                || !Objects.equals(details.userId(), event.userId()) || details.paidAt() == null
                || details.quantity() == null || details.quantity() <= 0) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        ShippingAddressDetails address = new ShippingAddressDetails(null, details.userId(), details.province(),
                details.city(), details.town(), details.mobile(), details.street(), details.contact());
        return shipping.createFromFrozenAddress(new CreateShippingOrderFromSnapshotCommand(
                details.orderId().toString(), details.userId(), details.skuCode(), details.productName(),
                details.imageUrl(), details.quantity()), address);
    }

    private ShippingDomainException error(ShippingErrorCode code) {
        return new ShippingDomainException(code);
    }
}
