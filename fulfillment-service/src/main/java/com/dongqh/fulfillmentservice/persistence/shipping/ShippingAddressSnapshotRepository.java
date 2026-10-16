package com.dongqh.fulfillmentservice.persistence.shipping;

import com.dongqh.fulfillmentservice.enums.shipping.ShippingSourceType;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface ShippingAddressSnapshotRepository {
    Optional<ShippingAddressSnapshot> findById(long id);

    List<ShippingAddressSnapshot> findByIds(Collection<Long> ids);

    Optional<ShippingAddressSnapshot> findBySource(ShippingSourceType sourceType, String sourceId);

    ShippingAddressSnapshot insert(ShippingAddressSnapshot snapshot);
}
