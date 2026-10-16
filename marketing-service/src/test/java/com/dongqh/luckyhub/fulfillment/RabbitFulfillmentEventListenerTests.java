package com.dongqh.luckyhub.fulfillment;

import com.dongqh.luckyhub.messaging.fulfillment.event.FulfillmentTaskReadyEvent;
import com.dongqh.luckyhub.messaging.fulfillment.rabbit.RabbitFulfillmentEventListener;
import com.dongqh.luckyhub.worker.fulfillment.FulfillmentWorker;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RabbitFulfillmentEventListenerTests {
    @Test void delegatesOnlyTheNamedTask(){FulfillmentWorker worker=mock(FulfillmentWorker.class);var listener=new RabbitFulfillmentEventListener(worker);listener.onMessage(new FulfillmentTaskReadyEvent("event-1","TASK-1",LocalDateTime.now()));verify(worker).runOne("TASK-1");}
    @Test void propagatesInfrastructureFailureForContainerRetry(){FulfillmentWorker worker=mock(FulfillmentWorker.class);var listener=new RabbitFulfillmentEventListener(worker);var event=new FulfillmentTaskReadyEvent("event-2","TASK-2",LocalDateTime.now());var failure=new DataAccessResourceFailureException("database unavailable");doThrow(failure).when(worker).runOne("TASK-2");assertThatThrownBy(()->listener.onMessage(event)).isSameAs(failure);}
}
