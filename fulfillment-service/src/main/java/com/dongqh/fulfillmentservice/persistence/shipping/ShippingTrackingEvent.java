package com.dongqh.fulfillmentservice.persistence.shipping;

import com.dongqh.fulfillmentservice.enums.shipping.TrackingEventType;

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
