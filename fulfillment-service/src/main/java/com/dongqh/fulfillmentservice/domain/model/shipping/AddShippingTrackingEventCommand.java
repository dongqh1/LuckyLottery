package com.dongqh.fulfillmentservice.domain.model.shipping;

import com.dongqh.fulfillmentservice.enums.shipping.TrackingEventType;

import java.time.LocalDateTime;

public record AddShippingTrackingEventCommand(TrackingEventType eventType, String locationSummary, String description,
                                             LocalDateTime eventTime) {
}
