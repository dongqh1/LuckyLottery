package com.dongqh.pay.messaging;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.pay.mapper.PayMessageOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PayOutboxServiceTest {
    @Test
    void persistsPendingPaymentSuccessEvent() throws Exception {
        PayMessageOutboxMapper mapper = mock(PayMessageOutboxMapper.class);
        ObjectMapper json = new ObjectMapper();
        PaySuccessMessage event = new PaySuccessMessage(
                "evt-101", 101L, 7L, 8500, 31L,
                LocalDateTime.of(2026, 9, 17, 12, 30));

        new PayOutboxService(mapper, json).append(event);

        ArgumentCaptor<PayMessageOutbox> row = ArgumentCaptor.forClass(PayMessageOutbox.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue().getEventId()).isEqualTo("evt-101");
        assertThat(row.getValue().getEventType()).isEqualTo("PAY_SUCCESS");
        assertThat(row.getValue().getStatus()).isEqualTo(PayOutboxStatus.PENDING);
        assertThat(row.getValue().getRetryCount()).isZero();
        assertThat(json.readValue(row.getValue().getPayload(), PaySuccessMessage.class)).isEqualTo(event);
    }
}
