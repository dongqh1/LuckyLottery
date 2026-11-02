package com.dongqh.fulfillment.domain.model.shipping;

public record CreateShippingOrderFromSnapshotCommand(String sourceId, long targetUserId,
                                                     String skuCode, String productName,
                                                     String imageUrl, int quantity) {
}
