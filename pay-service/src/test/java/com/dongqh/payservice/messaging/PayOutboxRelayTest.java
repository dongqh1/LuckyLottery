package com.dongqh.payservice.messaging;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.payservice.mapper.PayMessageOutboxMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayOutboxRelayTest {
    @Test
    void marksSentOnlyAfterPublisherAck() throws Exception {
        PayMessageOutboxMapper mapper = mock(PayMessageOutboxMapper.class);
        PaySuccessPublisher publisher = mock(PaySuccessPublisher.class);
        ObjectMapper json = new ObjectMapper();
        PayMessageOutbox row = row(json);
        when(mapper.selectRelayCandidates(any(), eq(10))).thenReturn(List.of(row));
        when(mapper.claim(eq(1L), anyString(), any(), any())).thenReturn(1);

        new PayOutboxRelay(mapper, publisher, json, 10, Duration.ofSeconds(30)).relay();

        verify(publisher).publish(any(PaySuccessMessage.class));
        verify(mapper).markSent(eq(1L), anyString(), any());
        verify(mapper, never()).markFailed(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void recordsFailureForLaterRetry() throws Exception {
        PayMessageOutboxMapper mapper = mock(PayMessageOutboxMapper.class);
        PaySuccessPublisher publisher = mock(PaySuccessPublisher.class);
        ObjectMapper json = new ObjectMapper();
        PayMessageOutbox row = row(json);
        when(mapper.selectRelayCandidates(any(), eq(10))).thenReturn(List.of(row));
        when(mapper.claim(eq(1L), anyString(), any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher).publish(any(PaySuccessMessage.class));

        new PayOutboxRelay(mapper, publisher, json, 10, Duration.ofSeconds(30)).relay();

        verify(mapper).markFailed(eq(1L), anyString(), contains("broker unavailable"), any());
        verify(mapper, never()).markSent(anyLong(), anyString(), any());
    }

    private PayMessageOutbox row(ObjectMapper json) throws Exception {
        PaySuccessMessage event = new PaySuccessMessage("evt-101", 101L, 7L, 8500, 31L,
                LocalDateTime.of(2026, 9, 17, 12, 30));
        PayMessageOutbox row = new PayMessageOutbox();
        row.setId(1L);
        row.setPayload(json.writeValueAsString(event));
        row.setRetryCount(0);
        row.setStatus(PayOutboxStatus.PENDING);
        return row;
    }
}
