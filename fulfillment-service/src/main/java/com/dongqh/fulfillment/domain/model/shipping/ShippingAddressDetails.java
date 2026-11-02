package com.dongqh.fulfillment.domain.model.shipping;

public record ShippingAddressDetails(
        Long id,
        Long userId,
        String province,
        String city,
        String town,
        String mobile,
        String street,
        String contact
) {
    @Override
    public String toString() {
        return "ShippingAddressDetails[id=" + id + ", userId=" + userId + ", pii=[REDACTED]]";
    }
}
