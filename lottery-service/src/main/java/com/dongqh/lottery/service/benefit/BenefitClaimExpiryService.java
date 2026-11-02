package com.dongqh.lottery.service.benefit;

import com.dongqh.lottery.mapper.drawtransaction.UserBenefitMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BenefitClaimExpiryService {
    private static final Logger log = LoggerFactory.getLogger(BenefitClaimExpiryService.class);
    private static final int SCAN_PAGE_SIZE = 50;

    private final UserBenefitMapper benefits;
    private final BenefitClaimExpiryWorker worker;
    private long scanAfterId;

    public BenefitClaimExpiryService(UserBenefitMapper benefits, BenefitClaimExpiryWorker worker) {
        this.benefits = benefits;
        this.worker = worker;
    }

    public synchronized int expireDue(int limit, LocalDateTime now) {
        if (limit < 1 || limit > 1000 || now == null) {
            throw new IllegalArgumentException("过期批次参数不合法");
        }
        int expired = 0;
        int candidates = 0;
        int candidateBudget = (int) Math.min(1000L, Math.max(SCAN_PAGE_SIZE, (long) limit * 4));
        while (expired < limit && candidates < candidateBudget) {
            int queryLimit = Math.min(SCAN_PAGE_SIZE, candidateBudget - candidates);
            List<Long> ids = benefits.selectDueClaimIdsAfter(now, scanAfterId, queryLimit);
            if (ids.isEmpty()) {
                scanAfterId = 0;
                break;
            }
            int processedThisPage = 0;
            for (Long id : ids) {
                scanAfterId = id;
                candidates++;
                processedThisPage++;
                try {
                    if (worker.expireOne(id, now)) expired++;
                } catch (RuntimeException exception) {
                    logCandidateFailure(id, exception);
                }
                if (expired == limit || candidates == candidateBudget) break;
            }
            if (processedThisPage == ids.size() && ids.size() < queryLimit) {
                scanAfterId = 0;
                break;
            }
        }
        return expired;
    }

    private void logCandidateFailure(long benefitId, RuntimeException exception) {
        String errorType = exception.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_$]", "_");
        if (errorType.isEmpty()) errorType = "RuntimeException";
        if (errorType.length() > 64) errorType = errorType.substring(0, 64);
        log.warn("Physical claim expiry candidate failed, benefitId={}, errorType={}", benefitId, errorType);
    }
}
