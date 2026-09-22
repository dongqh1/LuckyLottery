package com.dongqh.luckyhub.fulfillment.messaging.event;

import java.time.LocalDateTime;
import java.util.Objects;

public record FulfillmentTaskReadyEvent(
        String eventId,
        String fulfillmentNo,
        LocalDateTime occurredAt) {

    public FulfillmentTaskReadyEvent {
        eventId = required(eventId, "eventId");
        fulfillmentNo = required(fulfillmentNo, "fulfillmentNo");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
