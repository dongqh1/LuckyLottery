package com.dongqh.luckyhub.points.service.port;

import java.util.Optional;

public interface PointsRedeemableSkuPort {
    Optional<PointsRedeemableSkuSnapshot> getRedeemableSku(long skuId);
}
