package com.dongqh.points.domain.dto.points;

import com.dongqh.points.enums.points.PointsBusinessType;

public record PointsReversalCommand(
        Long userId,
        PointsBusinessType originalBusinessType,
        String originalBusinessId,
        String reversalBusinessId,
        String remark
) {
}
