package com.dongqh.points.domain.vo.points;

import com.dongqh.points.enums.points.PointsBusinessType;
import com.dongqh.points.enums.points.PointsDirection;

import java.time.LocalDateTime;

public record PointsLedgerView(
        Long id,
        Long userId,
        PointsBusinessType businessType,
        String businessId,
        PointsDirection direction,
        Long amount,
        Long balanceAfter,
        Long reversalOfLedgerId,
        String remark,
        LocalDateTime createdAt
) {
}
