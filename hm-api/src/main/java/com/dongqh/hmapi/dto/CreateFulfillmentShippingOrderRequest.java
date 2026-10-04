package com.dongqh.hmapi.dto;

public record CreateFulfillmentShippingOrderRequest(
        String sourceType,
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
    public CreateFulfillmentShippingOrderRequest(String sourceType, String sourceId, long targetUserId,
                                                 long addressId, Long hmallItemId, String skuCode,
                                                 String productName, String imageUrl, int quantity) {
        this(sourceType, sourceId, targetUserId, addressId, hmallItemId, skuCode,
                productName, imageUrl, quantity, null);
    }
}
