package com.dongqh.fulfillmentservice.messaging;

import com.dongqh.hmapi.dto.OrderPaidMessage;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeOrderPaidDeadLetterReplayServiceTests {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final OrderPaidMessage event = new OrderPaidMessage(UUID.randomUUID(), 42L, 7L,
            LocalDateTime.of(2026, 10, 21, 12, 0));
    private final TradeOrderPaidDeadLetterReplayService service =
            new TradeOrderPaidDeadLetterReplayService(rabbit, objectMapper, Duration.ofSeconds(1));

    @Test
    void republishesToTradeExchangeAndAcknowledgesDeadLetterOnlyAfterBrokerConfirm() throws Exception {
        GetResponse response = response(19L);
        when(rabbit.execute(any(ChannelCallback.class))).thenAnswer(invocation -> {
            ChannelCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(channel);
        });
        when(channel.basicGet(TradeOrderPaidDeadLetterReplayService.DEAD_LETTER_QUEUE, false))
                .thenReturn(response).thenReturn(null);
        when(objectMapper.readValue(any(String.class), eq(OrderPaidMessage.class))).thenReturn(event);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, "confirmed"));
            return null;
        }).when(rabbit).convertAndSend(eq(TradeOrderPaidDeadLetterReplayService.EVENT_EXCHANGE),
                eq(TradeOrderPaidDeadLetterReplayService.ROUTING_KEY), eq(event), any(CorrelationData.class));

        int replayed = service.replay(10);

        assertThat(replayed).isEqualTo(1);
        verify(channel).basicAck(19L, false);
        verify(channel, never()).basicNack(19L, false, true);
        verify(rabbit).convertAndSend(eq(TradeOrderPaidDeadLetterReplayService.EVENT_EXCHANGE),
                eq(TradeOrderPaidDeadLetterReplayService.ROUTING_KEY), eq(event), any(CorrelationData.class));
    }

    @Test
    void keepsDeadLetterQueuedWhenRepublishIsNotConfirmed() throws Exception {
        when(rabbit.execute(any(ChannelCallback.class))).thenAnswer(invocation -> {
            ChannelCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(channel);
        });
        when(channel.basicGet(TradeOrderPaidDeadLetterReplayService.DEAD_LETTER_QUEUE, false))
                .thenReturn(response(20L));
        when(objectMapper.readValue(any(String.class), eq(OrderPaidMessage.class))).thenReturn(event);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbit).convertAndSend(eq(TradeOrderPaidDeadLetterReplayService.EVENT_EXCHANGE),
                eq(TradeOrderPaidDeadLetterReplayService.ROUTING_KEY), eq(event), any(CorrelationData.class));

        assertThatThrownBy(() -> service.replay(1)).isInstanceOf(IllegalStateException.class);

        verify(channel).basicNack(20L, false, true);
        verify(channel, never()).basicAck(20L, false);
    }

    private GetResponse response(long tag) {
        return new GetResponse(new Envelope(tag, false, "trade.events.dlx", "trade.order.paid"),
                new AMQP.BasicProperties.Builder().contentType("application/json").build(),
                "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), 0);
    }
}
