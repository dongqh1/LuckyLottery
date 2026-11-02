package com.dongqh.lottery.domain.vo.drawchance;

import com.dongqh.lottery.enums.drawchance.DrawChanceBusinessType;
import com.dongqh.lottery.enums.drawchance.DrawChanceDirection;

import java.time.LocalDateTime;

public record DrawChanceLedgerView(Long id, long userId, Long operatorUserId,
                                   DrawChanceBusinessType businessType, String businessId,
                                   DrawChanceDirection direction, long amount, long availableAfter,
                                   long reservedAfter, String reason, LocalDateTime createdAt) {
}
