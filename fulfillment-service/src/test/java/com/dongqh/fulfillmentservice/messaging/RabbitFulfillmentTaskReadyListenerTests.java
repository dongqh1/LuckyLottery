package com.dongqh.fulfillmentservice.messaging;

import com.dongqh.fulfillmentservice.worker.FulfillmentWorker;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitFulfillmentTaskReadyListenerTests {

    @Test
    void delegatesOnlyTheTaskNamedByTheMessage() {
        FulfillmentWorker worker = mock(FulfillmentWorker.class);
        RabbitFulfillmentTaskReadyListener listener = new RabbitFulfillmentTaskReadyListener(worker);

        listener.onMessage(new FulfillmentTaskReadyEvent(
                "event-1", "TASK-1", LocalDateTime.of(2026, 9, 27, 18, 0)));

        verify(worker).runOne("TASK-1");
    }

    @Test
    void propagatesInfrastructureFailureSoTheContainerCanRetry() {
        FulfillmentWorker worker = mock(FulfillmentWorker.class);
        RabbitFulfillmentTaskReadyListener listener = new RabbitFulfillmentTaskReadyListener(worker);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        doThrow(failure).when(worker).runOne("TASK-2");

        assertThatThrownBy(() -> listener.onMessage(new FulfillmentTaskReadyEvent(
                "event-2", "TASK-2", LocalDateTime.of(2026, 9, 27, 18, 0))))
                .isSameAs(failure);
    }
}
