package com.dongqh.fulfillment.worker;

import com.dongqh.fulfillment.service.FulfillmentTaskStateService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class FulfillmentSchedulerTests {

    @Test
    void recoveryThenRetryReconciliationAndAgedPendingCompensationRunInOrder() {
        FulfillmentTaskStateService state = mock(FulfillmentTaskStateService.class);
        FulfillmentWorker worker = mock(FulfillmentWorker.class);
        FulfillmentScheduler scheduler = new FulfillmentScheduler(state, worker, 45);

        scheduler.poll();

        InOrder order = inOrder(state, worker);
        order.verify(state).recoverExpiredLeases(45);
        order.verify(worker).runBatch();
        order.verify(worker).runCompensationBatch();
        verifyNoMoreInteractions(state, worker);
    }

    @Test
    void schedulerUsesConfiguredPollCadence() throws NoSuchMethodException {
        Method poll = FulfillmentScheduler.class.getMethod("poll");
        Scheduled scheduled = poll.getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString()).isEqualTo("${luckyhub.fulfillment.poll-interval:5s}");
        assertThat(scheduled.initialDelayString()).isEqualTo("${luckyhub.fulfillment.initial-delay:60s}");
    }
}
