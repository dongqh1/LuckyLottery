package com.dongqh.fulfillment.domain.model.shipping;

import com.dongqh.fulfillment.enums.shipping.ShippingSourceType;

public record CreateShippingOrderCommand(
        ShippingSourceType sourceType,
        String sourceId,
        long targetUserId,
        long addressSnapshotId,
        Long hmallItemId,
        String skuCode,
        String productName,
        String imageUrl,
        int quantity,
        String claimRequestId
) {

    public CreateShippingOrderCommand {
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType不能为空");
        }
        sourceId = required(sourceId, "sourceId", 100);
        if (targetUserId <= 0 || addressSnapshotId <= 0 || (hmallItemId != null && hmallItemId <= 0)) {
            throw new IllegalArgumentException("用户、地址快照和商品ID必须为正数");
        }
        skuCode = required(skuCode, "skuCode", 64);
        productName = required(productName, "productName", 100);
        imageUrl = optional(imageUrl, 500);
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity必须大于0");
        }
        claimRequestId = optional(claimRequestId, 64);
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = optional(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return normalized;
    }

    private static String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("参数长度不合法");
        }
        return normalized;
    }
}
