package com.dongqh.luckyhub.fulfillment;

import com.dongqh.luckyhub.config.fulfillment.FulfillmentProperties;
import com.dongqh.luckyhub.scheduler.fulfillment.FulfillmentScheduler;
import com.dongqh.luckyhub.service.fulfillment.FulfillmentStateService;
import com.dongqh.luckyhub.worker.fulfillment.FulfillmentWorker;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class FulfillmentCompensationSchedulerTests {
    @Test
    void recoversExpiredLeasesBeforeRunningCompensationBatch() {
        FulfillmentStateService state = mock(FulfillmentStateService.class);
        FulfillmentWorker worker = mock(FulfillmentWorker.class);
        FulfillmentProperties properties = new FulfillmentProperties(
                Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofSeconds(30),
                Duration.ofSeconds(5), Duration.ofMinutes(1), Duration.ofSeconds(30),
                Duration.ofSeconds(5), Duration.ofMinutes(1), 50, 25, 100);

        new FulfillmentScheduler(state, worker, properties).poll();

        InOrder order = inOrder(state, worker);
        order.verify(state).recoverExpiredLeases(25);
        order.verify(worker).runCompensationBatch();
    }
}
