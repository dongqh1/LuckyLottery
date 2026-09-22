package com.dongqh.luckyhub.reward.model;

public record ProductRewardPayload(long itemId, String itemName, String itemImage,
                                   long itemPriceCent, int quantity) {
    public ProductRewardPayload {
        if (itemId <= 0 || itemName == null || itemName.isBlank()
                || itemPriceCent < 0 || quantity <= 0) {
            throw new IllegalArgumentException("商品奖励参数不合法");
        }
        itemImage = itemImage == null || itemImage.isBlank() ? null : itemImage.trim();
    }
}
