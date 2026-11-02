package com.dongqh.fulfillment.persistence.shipping;

import com.dongqh.fulfillment.enums.shipping.ShippingSourceType;

import java.time.LocalDateTime;

public record ShippingAddressSnapshot(
        Long id,
        String snapshotNo,
        Long userId,
        Long addressId,
        ShippingSourceType sourceType,
        String sourceId,
        String receiverCiphertext,
        String phoneCiphertext,
        String provinceCiphertext,
        String cityCiphertext,
        String districtCiphertext,
        String detailCiphertext,
        String receiverMasked,
        String phoneMasked,
        String regionMasked,
        LocalDateTime createdAt
) {

    public ShippingAddressSnapshot withId(Long newId) {
        return new ShippingAddressSnapshot(newId, snapshotNo, userId, addressId, sourceType, sourceId,
                receiverCiphertext, phoneCiphertext, provinceCiphertext, cityCiphertext, districtCiphertext,
                detailCiphertext, receiverMasked, phoneMasked, regionMasked, createdAt);
    }

    @Override
    public String toString() {
        return "ShippingAddressSnapshot[id=" + id + ", snapshotNo=" + snapshotNo + ", userId=" + userId
                + ", addressId=" + addressId + ", sourceType=" + sourceType + ", sourceId=" + sourceId
                + ", pii=[REDACTED]]";
    }
}
