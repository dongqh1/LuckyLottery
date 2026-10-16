package com.dongqh.luckyhub.domain.vo.shipping;

import com.dongqh.luckyhub.enums.shipping.AddressStatus;

import java.time.LocalDateTime;

public record ShippingAddressView(
        Long id,
        String receiverMasked,
        String phoneMasked,
        String regionMasked,
        boolean defaultAddress,
        AddressStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
