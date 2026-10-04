package com.dongqh.hmapi.dto;

import java.time.LocalDateTime;

public record FulfillmentStatusChangedEventV1(
        String eventId,
        String fulfillmentNo,
        String shippingNo,
        String sourceType,
        String sourceId,
        String status,
        LocalDateTime occurredAt
) {
}
