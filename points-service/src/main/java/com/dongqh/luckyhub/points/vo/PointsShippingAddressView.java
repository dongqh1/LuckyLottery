package com.dongqh.luckyhub.points.vo;

public record PointsShippingAddressView(
        Long addressSnapshotId,
        String addressSnapshotNo,
        String receiverMasked,
        String phoneMasked,
        String regionMasked
) {
}
