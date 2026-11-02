package com.dongqh.item.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.item.domain.dto.ItemDTO;
import com.dongqh.item.domain.po.Item;
import com.dongqh.item.domain.query.ItemPageQuery;
import com.dongqh.item.mapper.ItemMapper;
import com.dongqh.item.service.ItemSearchService;
import com.hmall.common.domain.PageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemSearchServiceImpl implements ItemSearchService {

    private static final int MAX_SUGGESTIONS = 10;

    private final ItemMapper itemMapper;

    @Override
    public PageDTO<ItemDTO> search(ItemPageQuery query) {
        normalizeSort(query);
        Page<Item> page = itemMapper.selectPage(
                query.toMpPage("update_time", false), buildQuery(query));
        return PageDTO.of(page, ItemDTO.class);
    }

    @Override
    public List<String> suggest(String key) {
        if (StrUtil.isBlank(key)) {
            return Collections.emptyList();
        }
        List<Item> items = itemMapper.selectList(Wrappers.<Item>lambdaQuery()
                .select(Item::getName)
                .eq(Item::getStatus, 1)
                .like(Item::getName, key.trim())
                .last("LIMIT 50"));
        return items.stream()
                .map(Item::getName)
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .limit(MAX_SUGGESTIONS)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, List<String>> filters(ItemPageQuery query) {
        List<Item> items = itemMapper.selectList(buildQuery(query)
                .select(Item::getCategory, Item::getBrand));
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("category", distinctValues(items, true));
        result.put("brand", distinctValues(items, false));
        return result;
    }

    private LambdaQueryWrapper<Item> buildQuery(ItemPageQuery query) {
        LambdaQueryWrapper<Item> wrapper = Wrappers.lambdaQuery(Item.class)
                .like(StrUtil.isNotBlank(query.getKey()), Item::getName, query.getKey())
                .eq(StrUtil.isNotBlank(query.getBrand()), Item::getBrand, query.getBrand())
                .eq(StrUtil.isNotBlank(query.getCategory()), Item::getCategory, query.getCategory())
                .eq(Item::getStatus, 1);
        if (query.getMinPrice() != null && query.getMaxPrice() != null) {
            wrapper.between(Item::getPrice, query.getMinPrice(), query.getMaxPrice());
        }
        return wrapper;
    }

    private List<String> distinctValues(List<Item> items, boolean category) {
        return items.stream()
                .map(item -> category ? item.getCategory() : item.getBrand())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }

    private void normalizeSort(ItemPageQuery query) {
        if (!"sold".equals(query.getSortBy()) && !"price".equals(query.getSortBy())) {
            query.setSortBy(null);
        }
    }
}
