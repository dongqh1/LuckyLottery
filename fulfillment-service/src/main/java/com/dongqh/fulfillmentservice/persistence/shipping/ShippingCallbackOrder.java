package com.dongqh.fulfillmentservice.persistence.shipping;

import com.dongqh.fulfillmentservice.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillmentservice.enums.shipping.ShippingStatus;

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
