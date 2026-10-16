package com.dongqh.luckyhub.service.lottery;

import com.dongqh.luckyhub.domain.model.lottery.ReconciliationResult;

import java.time.Instant;

public interface LotteryReconciliationService {

    ReconciliationResult reconcileExpiredReservations(Instant now);
}
