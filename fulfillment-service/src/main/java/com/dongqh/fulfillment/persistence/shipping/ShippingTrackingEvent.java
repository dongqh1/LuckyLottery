package com.dongqh.fulfillment.persistence.shipping;

import com.dongqh.fulfillment.enums.shipping.TrackingEventType;

import java.time.LocalDateTime;

public record ShippingTrackingEvent(
        long shippingOrderId,
        String providerEventId,
        String waybillNo,
        TrackingEventType eventType,
        String locationSummary,
        String description,
        LocalDateTime eventTime,
        LocalDateTime receivedAt
) {
}
