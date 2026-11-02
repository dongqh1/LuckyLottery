package com.dongqh.points.domain.vo.points;

public record PointsShippingAddressView(
        Long addressSnapshotId,
        String addressSnapshotNo,
        String receiverMasked,
        String phoneMasked,
        String regionMasked
) {
}
