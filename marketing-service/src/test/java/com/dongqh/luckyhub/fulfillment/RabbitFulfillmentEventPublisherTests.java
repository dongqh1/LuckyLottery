package com.dongqh.luckyhub.fulfillment;

import com.dongqh.luckyhub.messaging.fulfillment.event.FulfillmentTaskReadyEvent;
import com.dongqh.luckyhub.messaging.fulfillment.rabbit.FulfillmentRabbitTopology;
import com.dongqh.luckyhub.messaging.fulfillment.rabbit.RabbitFulfillmentEventPublisher;
import com.dongqh.luckyhub.config.lottery.MessagingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RabbitFulfillmentEventPublisherTests {
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    @Test
    void publishesToStableRouteAfterBrokerAck() {
        completeNextPublish(true, null, false);
        FulfillmentTaskReadyEvent event = event("event-ack");

        publisher(Duration.ofSeconds(1)).publish(event);

        verify(rabbitTemplate).convertAndSend(
                eq(FulfillmentRabbitTopology.EXCHANGE),
                eq(FulfillmentRabbitTopology.ROUTING_KEY),
                same(event),
                org.mockito.ArgumentMatchers.<CorrelationData>argThat(
                        correlation -> event.eventId().equals(correlation.getId())));
    }

    @Test
    void rejectsBrokerNack() {
        completeNextPublish(false, "exchange rejected publish", false);

        assertThatThrownBy(() -> publisher(Duration.ofSeconds(1)).publish(event("event-nack")))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("NACK")
                .hasMessageContaining("exchange rejected publish");
    }

    @Test
    void rejectsReturnedUnroutableEventEvenAfterAck() {
        completeNextPublish(true, null, true);

        assertThatThrownBy(() -> publisher(Duration.ofSeconds(1)).publish(event("event-return")))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("returned fulfillment event");
    }

    @Test
    void rejectsPublishWhenConfirmTimesOut() {
        assertThatThrownBy(() -> publisher(Duration.ofMillis(1)).publish(event("event-timeout")))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("timed out");
    }

    private void completeNextPublish(boolean ack, String reason, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (returned) {
                correlation.setReturned(new ReturnedMessage(
                        new Message(new byte[0], new MessageProperties()),
                        312, "NO_ROUTE", FulfillmentRabbitTopology.EXCHANGE,
                        FulfillmentRabbitTopology.ROUTING_KEY));
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(Object.class), any(CorrelationData.class));
    }

    private RabbitFulfillmentEventPublisher publisher(Duration timeout) {
        return new RabbitFulfillmentEventPublisher(rabbitTemplate,
                new MessagingProperties(true, "lottery-core", timeout, Duration.ofSeconds(30)));
    }

    private FulfillmentTaskReadyEvent event(String id) {
        return new FulfillmentTaskReadyEvent(id, "TASK-1", LocalDateTime.of(2026, 9, 20, 15, 0));
    }
}
