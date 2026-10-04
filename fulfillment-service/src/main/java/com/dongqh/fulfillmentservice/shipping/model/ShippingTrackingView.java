package com.dongqh.fulfillmentservice.shipping.model;

import com.dongqh.fulfillmentservice.shipping.enums.ShippingStatus;
import com.dongqh.fulfillmentservice.shipping.enums.TrackingEventType;

import java.time.LocalDateTime;
import java.util.List;

public record ShippingTrackingView(
        String shippingNo,
        ShippingStatus status,
        String carrierCode,
        String carrierName,
        String waybillNo,
        String productName,
        String imageUrl,
        Integer quantity,
        String receiverMasked,
        String phoneMasked,
        String regionMasked,
        List<TrackingEvent> tracking
) {
    public record TrackingEvent(
            TrackingEventType eventType,
            LocalDateTime eventTime,
            String locationSummary,
            String description
    ) {
    }
}
