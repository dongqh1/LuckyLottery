package com.dongqh.fulfillment.messaging;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitFulfillmentTaskReadyPublisherTests {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);

    @Test
    void publishesTheStableTaskReadyRouteOnlyAfterBrokerAck() {
        completeNextPublish(true, null, false);
        FulfillmentTaskReadyEvent event = event("event-ack");

        publisher(Duration.ofSeconds(1)).publish(event);

        verify(rabbit).convertAndSend(
                eq(FulfillmentRabbitTopology.EXCHANGE),
                eq(FulfillmentRabbitTopology.ROUTING_KEY),
                same(event),
                org.mockito.ArgumentMatchers.<CorrelationData>argThat(
                        correlation -> event.eventId().equals(correlation.getId())));
    }

    @Test
    void nackAndReturnedMessagesAreRejected() {
        completeNextPublish(false, "broker rejected publish", false);
        assertThatThrownBy(() -> publisher(Duration.ofSeconds(1)).publish(event("event-nack")))
                .isInstanceOf(AmqpException.class).hasMessageContaining("NACK");

        completeNextPublish(true, null, true);
        assertThatThrownBy(() -> publisher(Duration.ofSeconds(1)).publish(event("event-return")))
                .isInstanceOf(AmqpException.class).hasMessageContaining("returned fulfillment event");
    }

    @Test
    void confirmTimeoutLeavesTheOutboxRowRetryable() {
        assertThatThrownBy(() -> publisher(Duration.ofMillis(1)).publish(event("event-timeout")))
                .isInstanceOf(AmqpException.class).hasMessageContaining("timed out");
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
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
    }

    private RabbitFulfillmentTaskReadyPublisher publisher(Duration timeout) {
        return new RabbitFulfillmentTaskReadyPublisher(rabbit, timeout);
    }

    private FulfillmentTaskReadyEvent event(String id) {
        return new FulfillmentTaskReadyEvent(id, "TASK-1", LocalDateTime.of(2026, 9, 27, 18, 0));
    }
}
