package com.dongqh.luckyhub.domain.vo.shipping;

public record ShippingAddressSnapshotView(
        Long id,
        String snapshotNo,
        String receiverMasked,
        String phoneMasked,
        String regionMasked
) {
}
