package com.dongqh.fulfillment.persistence.shipping;

import com.dongqh.fulfillment.enums.shipping.TrackingEventType;

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
