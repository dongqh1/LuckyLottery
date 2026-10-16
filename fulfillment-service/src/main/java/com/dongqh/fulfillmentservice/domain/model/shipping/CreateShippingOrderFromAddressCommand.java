package com.dongqh.fulfillmentservice.domain.model.shipping;

import com.dongqh.fulfillmentservice.enums.shipping.ShippingSourceType;

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
