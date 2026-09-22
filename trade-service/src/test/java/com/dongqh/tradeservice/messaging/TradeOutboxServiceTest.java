package com.dongqh.tradeservice.messaging;

import com.dongqh.hmapi.dto.OrderCreatedMessage;
import com.dongqh.tradeservice.mapper.TradeMessageOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TradeOutboxServiceTest {
    @Test
    void persistsPendingEventWithTheOrderTransaction() throws Exception {
        TradeMessageOutboxMapper mapper = mock(TradeMessageOutboxMapper.class);
        ObjectMapper json = new ObjectMapper();
        OrderCreatedMessage event = new OrderCreatedMessage(
                UUID.randomUUID(), 101L, 7L, List.of(11L, 12L), LocalDateTime.now());

        new TradeOutboxService(mapper, json).append(event);

        ArgumentCaptor<TradeMessageOutbox> row = ArgumentCaptor.forClass(TradeMessageOutbox.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue().getStatus()).isEqualTo(TradeOutboxStatus.PENDING);
        assertThat(row.getValue().getRetryCount()).isZero();
        assertThat(json.readValue(row.getValue().getPayload(), OrderCreatedMessage.class)).isEqualTo(event);
    }
}
