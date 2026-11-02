package com.dongqh.fulfillment.domain.model.shipping;

import com.dongqh.fulfillment.enums.shipping.TrackingEventType;

import java.time.LocalDateTime;

public record AddShippingTrackingEventCommand(TrackingEventType eventType, String locationSummary, String description,
                                             LocalDateTime eventTime) {
}
