package com.dongqh.fulfillmentservice.shipping.persistence;

import com.dongqh.fulfillmentservice.shipping.enums.TrackingEventType;

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
