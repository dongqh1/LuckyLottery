package com.dongqh.luckyhub.domain.vo.points;

import com.dongqh.luckyhub.enums.points.PointsBusinessType;
import com.dongqh.luckyhub.enums.points.PointsDirection;

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
