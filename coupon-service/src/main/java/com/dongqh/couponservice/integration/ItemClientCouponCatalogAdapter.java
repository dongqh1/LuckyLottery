package com.dongqh.couponservice.integration;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.luckyhub.coupon.service.port.CouponCatalogPort;
import com.dongqh.luckyhub.coupon.service.port.CouponItemSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class ItemClientCouponCatalogAdapter implements CouponCatalogPort {
    private final ItemClient items;

    public ItemClientCouponCatalogAdapter(ItemClient items) {
        this.items = items;
    }

    @Override
    public CouponItemSnapshot getItem(long itemId) {
        List<ItemDTO> response = items.queryItemByIds(List.of(itemId));
        if (response == null) {
            return null;
        }
        List<ItemDTO> matches = response.stream()
                .filter(Objects::nonNull)
                .filter(item -> Long.valueOf(itemId).equals(item.getId()))
                .limit(2)
                .toList();
        if (matches.size() != 1) {
            return null;
        }
        ItemDTO item = matches.get(0);
        return new CouponItemSnapshot(item.getId(), item.getName(), item.getPrice(), item.getStock(),
                item.getCategory(), item.getBrand(), item.getStatus());
    }
}
