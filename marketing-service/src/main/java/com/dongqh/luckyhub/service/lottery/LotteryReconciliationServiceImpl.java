package com.dongqh.luckyhub.service.lottery;

import com.dongqh.luckyhub.config.lottery.LotteryProperties;
import com.dongqh.luckyhub.service.drawchance.DrawChanceService;
import com.dongqh.luckyhub.domain.model.lottery.ReconciliationResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class LotteryReconciliationServiceImpl implements LotteryReconciliationService {
    private final DrawChanceService drawChances;
    private final java.time.Duration processingTimeout;
    private final java.time.ZoneId zoneId;
    private final int batchSize;

    public LotteryReconciliationServiceImpl(DrawChanceService drawChances,
                                             LotteryProperties properties) {
        this.drawChances = Objects.requireNonNull(drawChances);
        this.processingTimeout = Objects.requireNonNull(properties.processingTimeout());
        this.zoneId = Objects.requireNonNull(properties.zoneId());
        this.batchSize = properties.reconcileBatchSize();
    }

    @Override
    public ReconciliationResult reconcileExpiredReservations(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        LocalDateTime cutoff = LocalDateTime.ofInstant(now, zoneId).minus(processingTimeout);
        int settled = drawChances.reconcileExpired(batchSize, cutoff);
        return new ReconciliationResult(0, 0, 0, 0, 0, 0);
    }
}
