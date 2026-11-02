package com.dongqh.lottery.scheduler.reconciliation;

import com.dongqh.lottery.service.reconciliation.LotteryReconciliationService;
import com.dongqh.lottery.service.reconciliation.LotteryReconciliationService.ReconciliationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "luckyhub.lottery", name = "reconciliation-enabled", havingValue = "true")
public class LotteryReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(LotteryReconciliationScheduler.class);

    private final LotteryReconciliationService reconciliation;

    public LotteryReconciliationScheduler(LotteryReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(
            fixedDelayString = "${luckyhub.lottery.reconcile-interval:30s}",
            initialDelayString = "${luckyhub.lottery.reconcile-initial-delay:60s}")
    public void reconcile() {
        ReconciliationSummary summary = reconciliation.reconcile(Instant.now());
        log.info("Lottery reconciliation completed: scanned={}, confirmed={}, released={}, timedOut={}, " +
                        "deferred={}, failed={}, settledDrawChances={}",
                summary.scanned(), summary.confirmed(), summary.released(), summary.timedOut(),
                summary.deferred(), summary.failed(), summary.settledDrawChances());
    }
}
