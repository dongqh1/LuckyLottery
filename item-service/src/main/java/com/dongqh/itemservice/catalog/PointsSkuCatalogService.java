package com.dongqh.itemservice.catalog;

import com.dongqh.luckyhub.domain.po.catalog.Product;
import com.dongqh.luckyhub.domain.po.catalog.ProductSku;
import com.dongqh.luckyhub.mapper.catalog.ProductMapper;
import com.dongqh.luckyhub.mapper.catalog.ProductSkuMapper;
import com.dongqh.luckyhub.domain.model.catalog.RedeemableSkuSnapshot;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PointsSkuCatalogService {

    private static final int ENABLED = 1;

    private final ProductMapper products;
    private final ProductSkuMapper skus;

    public PointsSkuCatalogService(ProductMapper products, ProductSkuMapper skus) {
        this.products = products;
        this.skus = skus;
    }

    public Optional<RedeemableSkuSnapshot> findRedeemableSku(long skuId) {
        if (skuId <= 0) {
            return Optional.empty();
        }
        ProductSku sku = skus.selectById(skuId);
        if (sku == null || !Integer.valueOf(ENABLED).equals(sku.getStatus())
                || !Boolean.TRUE.equals(sku.getPointsEnabled())
                || sku.getPointsPrice() == null || sku.getPointsPrice() <= 0) {
            return Optional.empty();
        }
        Product product = products.selectById(sku.getProductId());
        if (product == null || !Integer.valueOf(ENABLED).equals(product.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(new RedeemableSkuSnapshot(sku.getId(), product.getProductCode(),
                product.getProductName(), sku.getSkuCode(), sku.getSkuName(),
                product.getProductType(), product.getImageUrl(), sku.getPointsPrice()));
    }
}
