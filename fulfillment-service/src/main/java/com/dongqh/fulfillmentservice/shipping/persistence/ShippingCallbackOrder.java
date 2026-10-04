package com.dongqh.fulfillmentservice.shipping.persistence;

import com.dongqh.fulfillmentservice.shipping.enums.ShippingSourceType;
import com.dongqh.fulfillmentservice.shipping.enums.ShippingStatus;

import java.time.LocalDateTime;

public record ShippingCallbackOrder(
        long id,
        String shippingNo,
        ShippingSourceType sourceType,
        String sourceId,
        String fulfillmentNo,
        String waybillNo,
        ShippingStatus status,
        LocalDateTime deliveredAt,
        int version
) {
}
