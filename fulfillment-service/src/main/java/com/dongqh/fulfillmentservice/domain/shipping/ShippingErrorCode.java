package com.dongqh.fulfillmentservice.domain.shipping;

public enum ShippingErrorCode {
    ADDRESS_NOT_FOUND,
    ADDRESS_INVALID,
    SHIPPING_NOT_FOUND,
    SHIPPING_IDEMPOTENCY_CONFLICT,
    SHIPPING_STATE_CONFLICT,
    SHIPPING_REQUEST_INVALID
}
