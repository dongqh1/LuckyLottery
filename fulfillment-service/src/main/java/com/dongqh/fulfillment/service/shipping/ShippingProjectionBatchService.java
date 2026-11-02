package com.dongqh.fulfillment.service.shipping;

import com.dongqh.fulfillment.persistence.shipping.ShippingOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ShippingProjectionBatchService {

    private static final Logger log = LoggerFactory.getLogger(ShippingProjectionBatchService.class);
    private static final int MAX_SCAN = 100;
    private static final int FORCED_FAIRNESS_SCAN_INTERVAL = 10;

    private final ShippingOrderRepository orders;
    private final ShippingOrderProjectionWorker worker;
    private long cursorId;
    private long fairnessCursorId;
    private long fairnessHighWaterMarkId;
    private int batchesSinceFairnessScan;

    public ShippingProjectionBatchService(ShippingOrderRepository orders, ShippingOrderProjectionWorker worker) {
        this.orders = orders;
        this.worker = worker;
    }

    public synchronized int projectPending() {
        List<Long> candidates;
        if (++batchesSinceFairnessScan >= FORCED_FAIRNESS_SCAN_INTERVAL) {
            batchesSinceFairnessScan = 0;
            candidates = scanFairnessPage();
        } else {
            CandidateScan scan = scanFrom(cursorId);
            cursorId = scan.nextCursorId();
            candidates = scan.candidateIds();
        }

        int projected = 0;
        for (Long id : candidates) {
            try {
                if (worker.projectOne(id)) {
                    projected++;
                }
            } catch (RuntimeException ignored) {
                log.warn("单条物流状态投影失败 shippingOrderId={}", id);
            }
        }
        return projected;
    }

    private List<Long> scanFairnessPage() {
        if (fairnessHighWaterMarkId == 0) {
            fairnessHighWaterMarkId = orders.selectProjectionCandidateHighWaterMark();
            fairnessCursorId = 0;
        }
        if (fairnessHighWaterMarkId == 0) {
            return List.of();
        }

        List<Long> candidates = orders.selectProjectionCandidateIdsBetween(
                fairnessCursorId, fairnessHighWaterMarkId, MAX_SCAN);
        if (candidates.size() < MAX_SCAN
                || candidates.get(candidates.size() - 1) >= fairnessHighWaterMarkId) {
            fairnessHighWaterMarkId = 0;
            fairnessCursorId = 0;
        } else {
            fairnessCursorId = candidates.get(candidates.size() - 1);
        }
        return candidates;
    }

    private CandidateScan scanFrom(long cursor) {
        List<Long> afterCursor = orders.selectProjectionCandidateIdsAfter(cursor, MAX_SCAN);
        List<Long> candidates = new ArrayList<>(afterCursor);
        if (candidates.size() < MAX_SCAN && cursor > 0) {
            candidates.addAll(orders.selectProjectionCandidateIdsBefore(cursor, MAX_SCAN - candidates.size()));
        }
        long nextCursor = !afterCursor.isEmpty()
                ? afterCursor.get(afterCursor.size() - 1)
                : !candidates.isEmpty() ? candidates.get(candidates.size() - 1) : 0;
        return new CandidateScan(List.copyOf(candidates), nextCursor);
    }

    private record CandidateScan(List<Long> candidateIds, long nextCursorId) {
    }
}
