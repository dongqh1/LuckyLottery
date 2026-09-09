package com.dongqh.itemservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.itemservice.domain.dto.ItemDTO;
import com.dongqh.itemservice.domain.po.Item;
import com.dongqh.itemservice.domain.query.ItemPageQuery;
import com.dongqh.itemservice.mapper.ItemMapper;
import com.hmall.common.domain.PageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class ItemSearchServiceImplTest {

    @Mock
    private ItemMapper itemMapper;

    private ItemSearchServiceImpl service;

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Item.class);
    }

    @BeforeEach
    void setUp() {
        service = new ItemSearchServiceImpl(itemMapper);
    }

    @Test
    void searchReturnsDtosAndRestrictsResultsToActiveItems() {
        Item item = item("Phone", "Mobile", "Acme");
        item.setId(1L);
        item.setStatus(1);
        Page<Item> databasePage = new Page<>(1, 20, 1);
        databasePage.setRecords(Arrays.asList(item));
        when(itemMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(databasePage);

        PageDTO<ItemDTO> result = service.search(new ItemPageQuery());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).extracting(ItemDTO::getName).containsExactly("Phone");
        ArgumentCaptor<Wrapper<Item>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(itemMapper).selectPage(any(Page.class), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("status");
    }

    @Test
    void suggestionReturnsAtMostTenDistinctNonBlankNames() {
        List<Item> rows = IntStream.rangeClosed(1, 12)
                .mapToObj(index -> item("Phone " + index, "Mobile", "Acme"))
                .collect(Collectors.toCollection(ArrayList::new));
        rows.add(item("Phone 1", "Mobile", "Acme"));
        rows.add(item(" ", "Mobile", "Acme"));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(rows);

        List<String> result = service.suggest("Phone");

        assertThat(result).hasSize(10).doesNotHaveDuplicates().doesNotContain(" ");
    }

    @Test
    void filtersReturnDistinctNonBlankCategoriesAndBrands() {
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(
                item("A", "Mobile", "Acme"),
                item("B", "Mobile", "Acme"),
                item("C", "Laptop", " ")
        ));

        Map<String, List<String>> result = service.filters(new ItemPageQuery());

        assertThat(result.get("category")).containsExactly("Mobile", "Laptop");
        assertThat(result.get("brand")).containsExactly("Acme");
    }

    @Test
    void searchDoesNotBuildBetweenPredicateWhenOnePriceBoundIsMissing() {
        Page<Item> databasePage = new Page<>(1, 20, 0);
        databasePage.setRecords(new ArrayList<>());
        when(itemMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(databasePage);
        ItemPageQuery query = new ItemPageQuery();
        query.setMinPrice(100);

        service.search(query);

        ArgumentCaptor<Wrapper<Item>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(itemMapper).selectPage(any(Page.class), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment().toUpperCase()).doesNotContain("BETWEEN");
    }

    private Item item(String name, String category, String brand) {
        return new Item().setName(name).setCategory(category).setBrand(brand).setStatus(1);
    }
}
