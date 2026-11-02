package com.dongqh.pay.messaging;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaySuccessPublisherTest {
    @Test
    void publishesToPayExchangeAndWaitsForAck() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        PaySuccessMessage event = event();
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(anyString(), anyString(), same(event), any(CorrelationData.class));

        new PaySuccessPublisher(template, Duration.ofSeconds(1)).publish(event);

        verify(template).convertAndSend(eq(PayMessaging.EVENT_EXCHANGE),
                eq(PayMessaging.PAY_SUCCESS_KEY), same(event), any(CorrelationData.class));
    }

    @Test
    void rejectsBrokerNackSoOutboxCanRetry() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "rejected"));
            return null;
        }).when(template).convertAndSend(anyString(), anyString(), any(PaySuccessMessage.class),
                any(CorrelationData.class));

        assertThatThrownBy(() -> new PaySuccessPublisher(template, Duration.ofSeconds(1)).publish(event()))
                .isInstanceOf(AmqpException.class);
    }

    private PaySuccessMessage event() {
        return new PaySuccessMessage("evt-101", 101L, 7L, 8500, 31L,
                LocalDateTime.of(2026, 9, 17, 12, 30));
    }
}
