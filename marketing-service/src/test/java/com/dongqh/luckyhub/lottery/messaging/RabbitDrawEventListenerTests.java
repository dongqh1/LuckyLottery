package com.dongqh.luckyhub.lottery.messaging;

import com.dongqh.luckyhub.lottery.messaging.event.DrawConfirmedEvent;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventType;
import com.dongqh.luckyhub.lottery.messaging.rabbit.RabbitDrawEventListener;
import com.dongqh.luckyhub.lottery.service.MessageConsumeService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitDrawEventListenerTests {

    private final MessageConsumeService consumeService = mock(MessageConsumeService.class);
    private final RabbitDrawEventListener listener = new RabbitDrawEventListener(consumeService);

    @Test
    void delegatesTheExactEnvelopeToTheIdempotentConsumer() {
        DrawEventEnvelope event = event();

        listener.onMessage(event);

        verify(consumeService).consume(event);
    }

    @Test
    void propagatesConsumerFailureSoRabbitContainerCanRetryAndDeadLetter() {
        DrawEventEnvelope event = event();
        IllegalStateException failure = new IllegalStateException("database unavailable");
        doThrow(failure).when(consumeService).consume(event);

        assertThatThrownBy(() -> listener.onMessage(event)).isSameAs(failure);
    }

    private DrawEventEnvelope event() {
        return DrawEventEnvelope.create(
                DrawEventType.DRAW_CONFIRMED,
                "listener-" + UUID.randomUUID(),
                1L,
                2L,
                3L,
                LocalDateTime.of(2026, 9, 16, 20, 0),
                new DrawConfirmedEvent(1, LocalDate.of(2026, 9, 16)),
                new ObjectMapper());
    }
}
