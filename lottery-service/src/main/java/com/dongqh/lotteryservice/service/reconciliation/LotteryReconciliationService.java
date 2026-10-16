package com.dongqh.lotteryservice.service.reconciliation;

import com.dongqh.lotteryservice.config.LotteryProperties;
import com.dongqh.lotteryservice.service.drawchance.DrawChanceReservationService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Service
public class LotteryReconciliationService {
    private final DrawChanceReservationService drawChances;
    private final java.time.Duration processingTimeout;
    private final ZoneId zoneId;
    private final int batchSize;

    public LotteryReconciliationService(DrawChanceReservationService drawChances,
                                        LotteryProperties properties) {
        this.drawChances = Objects.requireNonNull(drawChances);
        this.processingTimeout = Objects.requireNonNull(properties.processingTimeout());
        this.zoneId = Objects.requireNonNull(properties.zoneId());
        this.batchSize = properties.reconcileBatchSize();
    }

    public ReconciliationSummary reconcile(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        LocalDateTime cutoff = LocalDateTime.ofInstant(now, zoneId).minus(processingTimeout);
        int settled = drawChances.reconcileExpired(batchSize, cutoff);
        return new ReconciliationSummary(0, 0, 0, 0, 0, 0, settled);
    }

    public record ReconciliationSummary(int scanned, int confirmed, int released, int timedOut,
                                         int deferred, int failed, int settledDrawChances) {
    }
}
