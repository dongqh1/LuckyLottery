package com.dongqh.lotteryservice.benefit.scheduler;

import com.dongqh.lotteryservice.benefit.service.BenefitClaimExpiryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "luckyhub.lottery.benefit-claim-expiry", name = "enabled", havingValue = "true")
public class BenefitClaimExpiryScheduler {

    private final BenefitClaimExpiryService expiry;
    private final int batchSize;

    public BenefitClaimExpiryScheduler(
            BenefitClaimExpiryService expiry,
            @Value("${luckyhub.lottery.benefit-claim-expiry.batch-size:50}") int batchSize
    ) {
        this.expiry = expiry;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${luckyhub.lottery.benefit-claim-expiry.refresh-interval:1m}",
            initialDelayString = "${luckyhub.lottery.benefit-claim-expiry.initial-delay:60s}"
    )
    public void expireDue() {
        expiry.expireDue(batchSize, LocalDateTime.now());
    }
}
