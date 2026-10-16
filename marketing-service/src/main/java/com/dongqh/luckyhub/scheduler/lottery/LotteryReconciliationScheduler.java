package com.dongqh.luckyhub.scheduler.lottery;

import com.dongqh.luckyhub.service.drawchance.DrawChanceService;
import com.dongqh.luckyhub.config.lottery.LotteryProperties;
import com.dongqh.luckyhub.service.lottery.LotteryReconciliationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "luckyhub.lottery", name = "reconciliation-enabled",
        havingValue = "true", matchIfMissing = true)
public class LotteryReconciliationScheduler {

    private final LotteryReconciliationService reconciliationService;
    private final DrawChanceService drawChanceService;
    private final LotteryProperties properties;

    public LotteryReconciliationScheduler(LotteryReconciliationService reconciliationService,
                                          DrawChanceService drawChanceService,
                                          LotteryProperties properties) {
        this.reconciliationService = reconciliationService;
        this.drawChanceService = drawChanceService;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${luckyhub.lottery.reconcile-interval:30s}",
            initialDelayString = "${luckyhub.lottery.reconcile-initial-delay:60s}")
    public void reconcileReservations() {
        reconciliationService.reconcileExpiredReservations(Instant.now());
        LocalDateTime cutoff = LocalDateTime.now(properties.zoneId())
                .minus(properties.processingTimeout());
        drawChanceService.reconcileExpired(properties.reconcileBatchSize(), cutoff);
    }
}
