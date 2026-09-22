package com.dongqh.tradeservice.messaging;

import com.dongqh.hmapi.dto.OrderCreatedMessage;
import com.dongqh.tradeservice.mapper.TradeMessageOutboxMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TradeOutboxRelayTest {
    @Test
    void marksSentOnlyAfterPublisherAck() throws Exception {
        TradeMessageOutboxMapper mapper = mock(TradeMessageOutboxMapper.class);
        OrderCreatedPublisher publisher = mock(OrderCreatedPublisher.class);
        ObjectMapper json = new ObjectMapper();
        TradeMessageOutbox row = row(json);
        when(mapper.selectRelayCandidates(any(), eq(10))).thenReturn(List.of(row));
        when(mapper.claim(eq(1L), anyString(), any(), any())).thenReturn(1);

        new TradeOutboxRelay(mapper, publisher, json, 10, Duration.ofSeconds(30)).relay();

        verify(publisher).publish(any(OrderCreatedMessage.class));
        verify(mapper).markSent(eq(1L), anyString(), any());
        verify(mapper, never()).markFailed(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void recordsFailureForLaterRetry() throws Exception {
        TradeMessageOutboxMapper mapper = mock(TradeMessageOutboxMapper.class);
        OrderCreatedPublisher publisher = mock(OrderCreatedPublisher.class);
        ObjectMapper json = new ObjectMapper();
        TradeMessageOutbox row = row(json);
        when(mapper.selectRelayCandidates(any(), eq(10))).thenReturn(List.of(row));
        when(mapper.claim(eq(1L), anyString(), any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("broker unavailable")).when(publisher)
                .publish(any(OrderCreatedMessage.class));

        new TradeOutboxRelay(mapper, publisher, json, 10, Duration.ofSeconds(30)).relay();

        verify(mapper).markFailed(eq(1L), anyString(), contains("broker unavailable"), any());
        verify(mapper, never()).markSent(anyLong(), anyString(), any());
    }

    private TradeMessageOutbox row(ObjectMapper json) throws Exception {
        OrderCreatedMessage event = new OrderCreatedMessage(
                UUID.randomUUID(), 101L, 7L, List.of(11L), LocalDateTime.now());
        TradeMessageOutbox row = new TradeMessageOutbox();
        row.setId(1L);
        row.setPayload(json.writeValueAsString(event));
        row.setRetryCount(0);
        row.setStatus(TradeOutboxStatus.PENDING);
        return row;
    }
}
