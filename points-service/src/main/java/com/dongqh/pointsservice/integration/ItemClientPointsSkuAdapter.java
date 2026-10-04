package com.dongqh.pointsservice.integration;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.dto.RedeemableSkuDTO;
import com.dongqh.luckyhub.points.service.port.PointsRedeemableSkuPort;
import com.dongqh.luckyhub.points.service.port.PointsRedeemableSkuSnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ItemClientPointsSkuAdapter implements PointsRedeemableSkuPort {

    private final ItemClient itemClient;

    public ItemClientPointsSkuAdapter(ItemClient itemClient) {
        this.itemClient = itemClient;
    }

    @Override
    public Optional<PointsRedeemableSkuSnapshot> getRedeemableSku(long skuId) {
        RedeemableSkuDTO sku = itemClient.getRedeemableSku(skuId);
        if (sku == null || sku.skuId() == null || sku.skuId() != skuId) {
            return Optional.empty();
        }
        return Optional.of(new PointsRedeemableSkuSnapshot(sku.skuId(), sku.productCode(), sku.productName(),
                sku.skuCode(), sku.skuName(), sku.productType(), sku.imageUrl(), sku.pointsPrice()));
    }
}
