package com.dongqh.luckyhub.integration.hmall;

public record HmallItemView(
        Long id,
        String name,
        Integer price,
        Integer stock,
        String image,
        String category,
        String brand,
        String spec,
        Integer status
) {
    public boolean availableForReward() {
        return id != null
                && Integer.valueOf(1).equals(status)
                && stock != null
                && stock > 0;
    }
}
