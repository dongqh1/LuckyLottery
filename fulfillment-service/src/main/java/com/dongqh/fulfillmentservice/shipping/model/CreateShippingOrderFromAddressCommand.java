package com.dongqh.fulfillmentservice.shipping.model;

import com.dongqh.fulfillmentservice.shipping.enums.ShippingSourceType;

public record CreateShippingOrderFromAddressCommand(
        ShippingSourceType sourceType,
        String sourceId,
        long targetUserId,
        long addressId,
        Long hmallItemId,
        String skuCode,
        String productName,
        String imageUrl,
        int quantity,
        String claimRequestId
) {
    public CreateShippingOrderFromAddressCommand(ShippingSourceType sourceType, String sourceId, long targetUserId,
                                                 long addressId, Long hmallItemId, String skuCode,
                                                 String productName, String imageUrl, int quantity) {
        this(sourceType, sourceId, targetUserId, addressId, hmallItemId, skuCode,
                productName, imageUrl, quantity, null);
    }
}
