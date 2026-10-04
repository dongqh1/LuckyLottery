package com.dongqh.fulfillmentservice.shipping.port;

import com.dongqh.fulfillmentservice.shipping.model.ShippingAddressDetails;

import java.util.Optional;

public interface ShippingAddressProvider {
    Optional<ShippingAddressDetails> findAddressForUser(long userId, long addressId);
}
