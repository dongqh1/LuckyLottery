package com.dongqh.luckyhub.domain.dto.points;

import com.dongqh.luckyhub.enums.points.PointsBusinessType;

public record PointsReversalCommand(
        Long userId,
        PointsBusinessType originalBusinessType,
        String originalBusinessId,
        String reversalBusinessId,
        String remark
) {
}
