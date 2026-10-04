package com.dongqh.fulfillmentservice.shipping.model;

import com.dongqh.fulfillmentservice.shipping.enums.TrackingEventType;

import java.time.LocalDateTime;

public record LogisticsCallbackCommand(
        String callbackId,
        String nonce,
        long timestampEpochSecond,
        String waybillNo,
        TrackingEventType eventType,
        LocalDateTime eventTime,
        String locationSummary,
        String description,
        String signature
) {
    @Override
    public String toString() {
        return "LogisticsCallbackCommand[REDACTED]";
    }
}
