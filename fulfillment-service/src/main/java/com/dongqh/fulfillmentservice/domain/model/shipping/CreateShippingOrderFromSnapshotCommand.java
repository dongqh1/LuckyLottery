package com.dongqh.fulfillmentservice.domain.model.shipping;

public record CreateShippingOrderFromSnapshotCommand(String sourceId, long targetUserId,
                                                     String skuCode, String productName,
                                                     String imageUrl, int quantity) {
}
