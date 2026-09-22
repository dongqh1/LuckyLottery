package com.dongqh.luckyhub.lottery.messaging;

import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import com.dongqh.luckyhub.lottery.messaging.event.DrawConfirmedEvent;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventType;
import com.dongqh.luckyhub.lottery.messaging.rabbit.LotteryRabbitTopology;
import com.dongqh.luckyhub.lottery.messaging.rabbit.RabbitDrawEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitDrawEventPublisherTests {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishesEveryEventTypeWithItsStableRoutingKeyAfterBrokerAck() {
        acknowledgeNextPublish(true, null, false);
        RabbitDrawEventPublisher publisher = publisher(Duration.ofSeconds(1));

        DrawEventEnvelope confirmed = event(DrawEventType.DRAW_CONFIRMED);
        publisher.publish(confirmed);
        verify(rabbitTemplate).convertAndSend(
                eq(LotteryRabbitTopology.EVENT_EXCHANGE),
                eq(LotteryRabbitTopology.DRAW_CONFIRMED_KEY),
                same(confirmed),
                org.mockito.ArgumentMatchers.<CorrelationData>argThat(
                        correlation -> confirmed.eventId().toString().equals(correlation.getId())));

        DrawEventEnvelope released = event(DrawEventType.DRAW_RELEASE_REQUESTED);
        publisher.publish(released);
        verify(rabbitTemplate).convertAndSend(
                eq(LotteryRabbitTopology.EVENT_EXCHANGE),
                eq(LotteryRabbitTopology.DRAW_RELEASE_REQUESTED_KEY),
                same(released),
                org.mockito.ArgumentMatchers.<CorrelationData>argThat(
                        correlation -> released.eventId().toString().equals(correlation.getId())));

        DrawEventEnvelope fulfillment = event(DrawEventType.PRIZE_FULFILLMENT_REQUESTED);
        publisher.publish(fulfillment);
        verify(rabbitTemplate).convertAndSend(
                eq(LotteryRabbitTopology.EVENT_EXCHANGE),
                eq(LotteryRabbitTopology.PRIZE_FULFILLMENT_REQUESTED_KEY),
                same(fulfillment),
                org.mockito.ArgumentMatchers.<CorrelationData>argThat(
                        correlation -> fulfillment.eventId().toString().equals(correlation.getId())));
    }

    @Test
    void rejectsBrokerNack() {
        acknowledgeNextPublish(false, "exchange rejected publish", false);

        assertThatThrownBy(() -> publisher(Duration.ofSeconds(1)).publish(event(DrawEventType.DRAW_CONFIRMED)))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("NACK")
                .hasMessageContaining("exchange rejected publish");
    }

    @Test
    void rejectsUnroutableReturnedMessageEvenWhenBrokerAcknowledgesPublish() {
        acknowledgeNextPublish(true, null, true);

        assertThatThrownBy(() -> publisher(Duration.ofSeconds(1)).publish(event(DrawEventType.DRAW_CONFIRMED)))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("unroutable")
                .hasMessageContaining("NO_ROUTE");
    }

    @Test
    void rejectsPublishWhenBrokerConfirmTimesOut() {
        assertThatThrownBy(() -> publisher(Duration.ofMillis(1)).publish(event(DrawEventType.DRAW_CONFIRMED)))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("timed out");
    }

    private void acknowledgeNextPublish(boolean ack, String reason, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (returned) {
                correlation.setReturned(new ReturnedMessage(
                        new Message(new byte[0], new MessageProperties()),
                        312,
                        "NO_ROUTE",
                        LotteryRabbitTopology.EVENT_EXCHANGE,
                        LotteryRabbitTopology.DRAW_CONFIRMED_KEY));
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(Object.class), any(CorrelationData.class));
    }

    private RabbitDrawEventPublisher publisher(Duration timeout) {
        return new RabbitDrawEventPublisher(
                rabbitTemplate,
                new MessagingProperties(true, "lottery-core", timeout, Duration.ofSeconds(30)));
    }

    private DrawEventEnvelope event(DrawEventType type) {
        return DrawEventEnvelope.create(
                type,
                "request-" + UUID.randomUUID(),
                1L,
                2L,
                3L,
                LocalDateTime.of(2026, 9, 16, 20, 0),
                new DrawConfirmedEvent(1, LocalDate.of(2026, 9, 16)),
                objectMapper);
    }
}
