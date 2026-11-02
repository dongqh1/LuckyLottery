package com.dongqh.item.service;

import com.dongqh.item.domain.dto.ItemDTO;
import com.dongqh.item.domain.query.ItemPageQuery;
import com.hmall.common.domain.PageDTO;

import java.util.List;
import java.util.Map;

public interface ItemSearchService {

    PageDTO<ItemDTO> search(ItemPageQuery query);

    List<String> suggest(String key);

    Map<String, List<String>> filters(ItemPageQuery query);
}
