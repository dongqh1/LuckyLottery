package com.dongqh.pointsservice.integration;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.luckyhub.points.service.port.PointsItemCatalogPort;
import com.dongqh.luckyhub.points.service.port.PointsItemSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class ItemClientPointsCatalogAdapter implements PointsItemCatalogPort {

    private final ItemClient itemClient;

    public ItemClientPointsCatalogAdapter(ItemClient itemClient) {
        this.itemClient = itemClient;
    }

    @Override
    public Optional<PointsItemSnapshot> getItem(long itemId) {
        List<ItemDTO> items = itemClient.queryItemByIds(List.of(itemId));
        if (items == null || items.size() != 1) {
            return Optional.empty();
        }
        ItemDTO item = items.get(0);
        if (item == null || !Objects.equals(item.getId(), itemId)) {
            return Optional.empty();
        }
        return Optional.of(new PointsItemSnapshot(item.getId(), item.getName(), item.getPrice(),
                item.getStock(), item.getImage(), item.getStatus()));
    }
}
