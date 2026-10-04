package com.dongqh.couponservice.coupon;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.couponservice.integration.ItemClientCouponCatalogAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemClientCouponCatalogAdapterTests {

    @Test
    void mapsOnlyTheRequestedItemFromTheCatalogContract() {
        ItemClient items = mock(ItemClient.class);
        ItemDTO matching = item(55L, 105, 3, "Case", "Brand", 1);
        ItemDTO unrelated = item(56L, 999, 9, "Other", "Other", 1);
        when(items.queryItemByIds(List.of(55L))).thenReturn(List.of(unrelated, matching));

        var item = new ItemClientCouponCatalogAdapter(items).getItem(55L);

        assertThat(item).isNotNull();
        assertThat(item.id()).isEqualTo(55L);
        assertThat(item.price()).isEqualTo(105);
        assertThat(item.category()).isEqualTo("Case");
    }

    @Test
    void returnsUnavailableWhenCatalogCannotFindTheRequestedItem() {
        ItemClient items = mock(ItemClient.class);
        when(items.queryItemByIds(List.of(55L))).thenReturn(List.of());

        assertThat(new ItemClientCouponCatalogAdapter(items).getItem(55L)).isNull();
    }

    private ItemDTO item(long id, int price, int stock, String category, String brand, int status) {
        ItemDTO item = new ItemDTO();
        item.setId(id);
        item.setName("Item " + id);
        item.setPrice(price);
        item.setStock(stock);
        item.setCategory(category);
        item.setBrand(brand);
        item.setStatus(status);
        return item;
    }
}
