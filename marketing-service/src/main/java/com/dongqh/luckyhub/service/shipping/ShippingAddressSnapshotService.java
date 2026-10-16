package com.dongqh.luckyhub.service.shipping;

import com.dongqh.luckyhub.domain.po.shipping.ShippingAddressSnapshot;
import com.dongqh.luckyhub.enums.shipping.ShippingSourceType;

public interface ShippingAddressSnapshotService {
    ShippingAddressSnapshot create(long userId, long addressId,
                                   ShippingSourceType sourceType, String sourceId);

    ShippingAddressSnapshot require(long snapshotId);
}
