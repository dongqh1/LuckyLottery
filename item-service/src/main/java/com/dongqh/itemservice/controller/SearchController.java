package com.dongqh.itemservice.controller;

import com.dongqh.itemservice.domain.dto.ItemDTO;
import com.dongqh.itemservice.domain.query.ItemPageQuery;
import com.dongqh.itemservice.service.ItemSearchService;
import com.hmall.common.domain.PageDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Api(tags = "商品搜索接口")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final ItemSearchService itemSearchService;

    @ApiOperation("搜索商品")
    @GetMapping("/list")
    public PageDTO<ItemDTO> search(ItemPageQuery query) {
        return itemSearchService.search(query);
    }

    @ApiOperation("搜索词联想")
    @GetMapping("/suggestion")
    public List<String> suggest(@RequestParam String key) {
        return itemSearchService.suggest(key);
    }

    @ApiOperation("搜索筛选项")
    @PostMapping("/filters")
    public Map<String, List<String>> filters(@RequestBody ItemPageQuery query) {
        return itemSearchService.filters(query);
    }
}
