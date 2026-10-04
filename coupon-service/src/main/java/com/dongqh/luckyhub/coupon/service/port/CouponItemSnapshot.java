package com.dongqh.luckyhub.coupon.service.port;

public record CouponItemSnapshot(
        Long id,
        String name,
        Integer price,
        Integer stock,
        String category,
        String brand,
        Integer status
) {
    public boolean availableForReward() {
        return id != null && Integer.valueOf(1).equals(status) && stock != null && stock > 0;
    }
}
