package com.dongqh.luckyhub.lottery.messaging;

import com.dongqh.luckyhub.domain.po.lottery.MessageOutbox;
import com.dongqh.luckyhub.enums.lottery.OutboxStatus;
import com.dongqh.luckyhub.mapper.lottery.MessageOutboxMapper;
import com.dongqh.luckyhub.messaging.lottery.event.DrawConfirmedEvent;
import com.dongqh.luckyhub.messaging.lottery.event.DrawEventEnvelope;
import com.dongqh.luckyhub.messaging.lottery.event.DrawEventType;
import com.dongqh.luckyhub.messaging.lottery.port.DrawEventPublisher;
import com.dongqh.luckyhub.service.lottery.OutboxRelayService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayServiceTests {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final MessageOutboxMapper mapper = mock(MessageOutboxMapper.class);
    private final DrawEventPublisher publisher = mock(DrawEventPublisher.class);

    @Test
    void marksOutboxSentOnlyAfterPublisherReturnsSuccessfully() throws Exception {
        DrawEventEnvelope event = event();
        MessageOutbox row = row(event, objectMapper.writeValueAsString(event));
        arrangeClaim(row);
        when(mapper.markSent(anyLong(), anyString(), any(LocalDateTime.class))).thenReturn(1);

        new OutboxRelayService(mapper, publisher, objectMapper, 10).relayBatch();

        verify(publisher).publish(event);
        verify(mapper).markSent(anyLong(), anyString(), any(LocalDateTime.class));
        verify(mapper, never()).markFailed(anyLong(), anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void recordsRetryableFailureWhenPublisherRejectsDelivery() throws Exception {
        DrawEventEnvelope event = event();
        MessageOutbox row = row(event, objectMapper.writeValueAsString(event));
        arrangeClaim(row);
        org.mockito.Mockito.doThrow(new IllegalStateException("broker NACK"))
                .when(publisher).publish(event);

        new OutboxRelayService(mapper, publisher, objectMapper, 10).relayBatch();

        verify(mapper, never()).markSent(anyLong(), anyString(), any(LocalDateTime.class));
        verify(mapper).markFailed(
                org.mockito.ArgumentMatchers.eq(row.getId()),
                anyString(),
                org.mockito.ArgumentMatchers.contains("broker NACK"),
                any(LocalDateTime.class));
    }

    @Test
    void malformedEnvelopeIsFailedWithoutCallingBrokerPublisher() {
        DrawEventEnvelope event = event();
        MessageOutbox row = row(event, "{}");
        arrangeClaim(row);

        new OutboxRelayService(mapper, publisher, objectMapper, 10).relayBatch();

        verify(publisher, never()).publish(any());
        verify(mapper).markFailed(
                org.mockito.ArgumentMatchers.eq(row.getId()),
                anyString(),
                anyString(),
                any(LocalDateTime.class));
    }

    private void arrangeClaim(MessageOutbox row) {
        when(mapper.selectRelayCandidates(any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(10)))
                .thenReturn(List.of(row));
        when(mapper.claimForRelay(
                org.mockito.ArgumentMatchers.eq(row.getId()),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(1);
    }

    private DrawEventEnvelope event() {
        return DrawEventEnvelope.create(
                DrawEventType.DRAW_CONFIRMED,
                "relay-" + UUID.randomUUID(),
                1L,
                2L,
                3L,
                LocalDateTime.of(2026, 9, 16, 20, 0),
                new DrawConfirmedEvent(1, LocalDate.of(2026, 9, 16)),
                objectMapper);
    }

    private MessageOutbox row(DrawEventEnvelope event, String payload) {
        MessageOutbox row = new MessageOutbox();
        row.setId(100L);
        row.setEventId(event.eventId().toString());
        row.setEventType(event.eventType().name());
        row.setEventVersion(event.eventVersion());
        row.setAggregateType("LOTTERY_DRAW_ORDER");
        row.setAggregateId(event.orderId().toString());
        row.setPayload(payload);
        row.setStatus(OutboxStatus.PENDING);
        row.setRetryCount(0);
        return row;
    }
}
