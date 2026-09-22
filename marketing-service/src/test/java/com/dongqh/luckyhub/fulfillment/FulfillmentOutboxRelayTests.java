package com.dongqh.luckyhub.fulfillment;

import com.dongqh.luckyhub.fulfillment.messaging.event.FulfillmentTaskReadyEvent;
import com.dongqh.luckyhub.fulfillment.mapper.FulfillmentOutboxMapper;
import com.dongqh.luckyhub.fulfillment.messaging.outbox.*;
import com.dongqh.luckyhub.fulfillment.messaging.rabbit.FulfillmentEventPublisher;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FulfillmentOutboxRelayTests {
    @Test void marksSentOnlyAfterConfirmedPublish() throws Exception {
        var json=JsonMapper.builder().findAndAddModules().build();var mapper=mock(FulfillmentOutboxMapper.class);var publisher=mock(FulfillmentEventPublisher.class);var event=new FulfillmentTaskReadyEvent("event-1","TASK-1",LocalDateTime.now());var row=FulfillmentOutbox.pending(event.eventId(),event.fulfillmentNo(),"FULFILLMENT_TASK_READY",json.writeValueAsString(event));row.setId(1L);when(mapper.selectRelayCandidates(any(),eq(10))).thenReturn(List.of(row));when(mapper.claimForRelay(eq(1L),anyString(),any(),any())).thenReturn(1);when(mapper.markSent(eq(1L),anyString(),any())).thenReturn(1);
        new FulfillmentOutboxRelay(mapper,publisher,json,10,Duration.ofSeconds(30)).relayBatch();
        verify(publisher).publish(event);verify(mapper).markSent(eq(1L),anyString(),any());verify(mapper,never()).markFailed(anyLong(),anyString(),anyString(),any());
    }
    @Test void recordsFailureWhenBrokerRejectsEvent() throws Exception {
        var json=JsonMapper.builder().findAndAddModules().build();var mapper=mock(FulfillmentOutboxMapper.class);var publisher=mock(FulfillmentEventPublisher.class);var event=new FulfillmentTaskReadyEvent("event-2","TASK-2",LocalDateTime.now());var row=FulfillmentOutbox.pending(event.eventId(),event.fulfillmentNo(),"FULFILLMENT_TASK_READY",json.writeValueAsString(event));row.setId(2L);when(mapper.selectRelayCandidates(any(),eq(10))).thenReturn(List.of(row));when(mapper.claimForRelay(eq(2L),anyString(),any(),any())).thenReturn(1);doThrow(new IllegalStateException("broker NACK")).when(publisher).publish(event);
        new FulfillmentOutboxRelay(mapper,publisher,json,10,Duration.ofSeconds(30)).relayBatch();
        verify(mapper).markFailed(eq(2L),anyString(),contains("broker NACK"),any());verify(mapper,never()).markSent(anyLong(),anyString(),any());
    }
    @Test void malformedEventIsFailedWithoutPublishing(){
        var json=JsonMapper.builder().findAndAddModules().build();var mapper=mock(FulfillmentOutboxMapper.class);var publisher=mock(FulfillmentEventPublisher.class);var row=FulfillmentOutbox.pending("event-3","TASK-3","FULFILLMENT_TASK_READY","{}");row.setId(3L);when(mapper.selectRelayCandidates(any(),eq(10))).thenReturn(List.of(row));when(mapper.claimForRelay(eq(3L),anyString(),any(),any())).thenReturn(1);
        new FulfillmentOutboxRelay(mapper,publisher,json,10,Duration.ofSeconds(30)).relayBatch();
        verify(publisher,never()).publish(any());verify(mapper).markFailed(eq(3L),anyString(),anyString(),any());verify(mapper,never()).markSent(anyLong(),anyString(),any());
    }
}
