package com.dongqh.luckyhub.service.catalog;

import com.dongqh.luckyhub.domain.dto.catalog.CreateProductCommand;
import com.dongqh.luckyhub.domain.dto.catalog.ProductQuery;
import com.dongqh.luckyhub.domain.model.catalog.RedeemableSkuSnapshot;
import com.dongqh.luckyhub.domain.model.catalog.PurchasableSkuSnapshot;
import com.dongqh.luckyhub.domain.vo.catalog.ProductView;
import com.dongqh.luckyhub.common.result.PageResponse;

import java.util.Optional;

public interface CatalogService {
    ProductView create(CreateProductCommand command);

    ProductView get(long productId);

    PageResponse<ProductView> page(ProductQuery query);

    Optional<RedeemableSkuSnapshot> findRedeemableSku(long skuId);
    Optional<PurchasableSkuSnapshot> findPurchasableSku(long skuId);
}
