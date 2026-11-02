package com.dongqh.fulfillment.persistence.shipping;

import com.dongqh.fulfillment.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillment.enums.shipping.ShippingStatus;

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
