package com.dongqh.hmapi.dto;

public record CreateShippingOrderRequest(String sourceType, String sourceId, long targetUserId,
                                         Long addressId, Long hmallItemId, String skuCode,
                                         String productName, String imageUrl, int quantity,
                                         String claimRequestId) {
    public CreateShippingOrderRequest(String sourceType, String sourceId, long targetUserId,
                                      Long addressId, Long hmallItemId, String skuCode,
                                      String productName, String imageUrl, int quantity) {
        this(sourceType, sourceId, targetUserId, addressId, hmallItemId, skuCode,
                productName, imageUrl, quantity, null);
    }
}
