package com.dongqh.fulfillmentservice.shipping.persistence;

import com.dongqh.fulfillmentservice.shipping.enums.TrackingEventType;

import java.time.LocalDateTime;

public record ShippingCallbackReceipt(
        String callbackId,
        String nonceDigest,
        String signatureDigest,
        String waybillNo,
        TrackingEventType eventType,
        LocalDateTime eventTime,
        String status,
        String errorCode,
        LocalDateTime receivedAt
) {
}
