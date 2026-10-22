package com.dongqh.lotteryservice.domain.vo.drawchance;

import com.dongqh.lotteryservice.enums.drawchance.DrawChanceBusinessType;
import com.dongqh.lotteryservice.enums.drawchance.DrawChanceDirection;

import java.time.LocalDateTime;

public record DrawChanceLedgerView(Long id, long userId, Long operatorUserId,
                                   DrawChanceBusinessType businessType, String businessId,
                                   DrawChanceDirection direction, long amount, long availableAfter,
                                   long reservedAfter, String reason, LocalDateTime createdAt) {
}
