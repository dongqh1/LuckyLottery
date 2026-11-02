package com.dongqh.points.domain.dto.points;

import com.dongqh.points.enums.points.PointsBusinessType;

public record PointsMutationCommand(
        Long userId,
        PointsBusinessType businessType,
        String businessId,
        Long amount,
        String remark
) {
}
