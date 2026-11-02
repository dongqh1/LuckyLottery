package com.dongqh.fulfillment.domain.model.shipping;

import com.dongqh.fulfillment.enums.shipping.ShippingStatus;
import com.dongqh.fulfillment.enums.shipping.TrackingEventType;

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
