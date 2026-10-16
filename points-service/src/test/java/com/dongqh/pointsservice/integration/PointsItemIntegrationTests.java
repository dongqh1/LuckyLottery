package com.dongqh.pointsservice.integration;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.luckyhub.points.integration.PointsIntegrationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointsItemIntegrationTests {

    private final ItemClient itemClient = mock(ItemClient.class);
    private final PointsIntegrationService integration = new PointsIntegrationService(itemClient, null);

    @Test
    void mapsOneMatchingItemFromItemServiceSnapshot() {
        ItemDTO item = item(1001L);
        when(itemClient.queryItemByIds(List.of(1001L))).thenReturn(List.of(item));

        var snapshot = integration.getItem(1001L);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().id()).isEqualTo(1001L);
        assertThat(snapshot.orElseThrow().name()).isEqualTo("商城耳机");
        assertThat(snapshot.orElseThrow().priceCent()).isEqualTo(9_900);
        assertThat(snapshot.orElseThrow().stock()).isEqualTo(8);
        assertThat(snapshot.orElseThrow().status()).isEqualTo(1);
    }

    @Test
    void treatsFallbackAndMismatchedItemResponseAsMissing() {
        when(itemClient.queryItemByIds(List.of(1001L))).thenReturn(List.of());
        assertThat(integration.getItem(1001L)).isEmpty();

        when(itemClient.queryItemByIds(List.of(1001L))).thenReturn(List.of(item(1002L)));
        assertThat(integration.getItem(1001L)).isEmpty();
    }

    private ItemDTO item(long id) {
        ItemDTO item = new ItemDTO();
        item.setId(id);
        item.setName("商城耳机");
        item.setPrice(9_900);
        item.setStock(8);
        item.setImage("/imgs/1001.jpg");
        item.setStatus(1);
        return item;
    }
}
