package com.dongqh.fulfillmentservice.service.shipping;

import com.dongqh.fulfillmentservice.domain.model.shipping.ShippingOrderView;
import com.dongqh.fulfillmentservice.domain.model.shipping.ShippingTrackingView;
import com.dongqh.fulfillmentservice.persistence.shipping.LogisticsCallbackRepository;
import com.dongqh.fulfillmentservice.persistence.shipping.ShippingTrackingEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ShippingTrackingQueryService {
    private final ShippingOrderService orders;
    private final LogisticsCallbackRepository callbacks;

    public ShippingTrackingQueryService(ShippingOrderService orders, LogisticsCallbackRepository callbacks) {
        this.orders = orders;
        this.callbacks = callbacks;
    }

    @Transactional(readOnly = true)
    public ShippingTrackingView getForUser(long userId, String shippingNo) {
        ShippingOrderView order = orders.getForUser(userId, shippingNo);
        List<ShippingTrackingView.TrackingEvent> tracking = callbacks
                .findTrackingByShippingOrderId(order.id()).stream()
                .map(ShippingTrackingQueryService::view)
                .toList();
        return new ShippingTrackingView(order.shippingNo(), order.status(), order.carrierCode(), order.carrierName(),
                order.waybillNo(), order.productName(), order.imageUrl(), order.quantity(), order.receiverMasked(),
                order.phoneMasked(), order.regionMasked(), tracking);
    }

    private static ShippingTrackingView.TrackingEvent view(ShippingTrackingEvent event) {
        return new ShippingTrackingView.TrackingEvent(event.eventType(), event.eventTime(),
                event.locationSummary(), event.description());
    }
}
