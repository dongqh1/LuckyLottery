package com.dongqh.luckyhub.domain.dto.points;

import com.dongqh.luckyhub.enums.points.PointsBusinessType;

public record PointsMutationCommand(
        Long userId,
        PointsBusinessType businessType,
        String businessId,
        Long amount,
        String remark
) {
}
