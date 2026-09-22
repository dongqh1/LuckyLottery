package com.dongqh.tradeservice.messaging;

import com.dongqh.hmapi.dto.OrderCreatedMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderCreatedPublisherTest {

    @Test
    void publishesToTradeExchangeAndWaitsForAck() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        OrderCreatedMessage event = event();
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(anyString(), anyString(), same(event), any(CorrelationData.class));

        new OrderCreatedPublisher(template, Duration.ofSeconds(1)).publish(event);

        verify(template).convertAndSend(eq(OrderMessaging.EVENT_EXCHANGE),
                eq(OrderMessaging.ORDER_CREATED_KEY), same(event), any(CorrelationData.class));
    }

    @Test
    void rejectsBrokerNackSoOutboxCanRetry() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "rejected"));
            return null;
        }).when(template).convertAndSend(anyString(), anyString(), any(OrderCreatedMessage.class),
                any(CorrelationData.class));

        assertThatThrownBy(() -> new OrderCreatedPublisher(template, Duration.ofSeconds(1)).publish(event()))
                .isInstanceOf(AmqpException.class);
    }

    private OrderCreatedMessage event() {
        return new OrderCreatedMessage(UUID.randomUUID(), 101L, 7L, List.of(11L), LocalDateTime.now());
    }
}
