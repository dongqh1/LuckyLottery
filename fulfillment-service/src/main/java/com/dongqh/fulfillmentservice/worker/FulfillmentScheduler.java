package com.dongqh.fulfillmentservice.worker;

import com.dongqh.fulfillmentservice.service.FulfillmentTaskStateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FulfillmentScheduler {

    private final FulfillmentTaskStateService state;
    private final FulfillmentWorker worker;
    private final int recoveryBatchSize;

    public FulfillmentScheduler(
            FulfillmentTaskStateService state,
            FulfillmentWorker worker,
            @Value("${luckyhub.fulfillment.recovery-batch-size:100}") int recoveryBatchSize
    ) {
        if (recoveryBatchSize < 1 || recoveryBatchSize > 1000) {
            throw new IllegalArgumentException("recoveryBatchSize必须为1到1000");
        }
        this.state = state;
        this.worker = worker;
        this.recoveryBatchSize = recoveryBatchSize;
    }

    @Scheduled(
            fixedDelayString = "${luckyhub.fulfillment.poll-interval:5s}",
            initialDelayString = "${luckyhub.fulfillment.initial-delay:60s}"
    )
    public void poll() {
        state.recoverExpiredLeases(recoveryBatchSize);
        worker.runBatch();
        worker.runCompensationBatch();
    }
}
