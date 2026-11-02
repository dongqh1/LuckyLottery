package com.dongqh.fulfillment.messaging;

import com.dongqh.hmapi.dto.OrderPaidMessage;
import com.rabbitmq.client.GetResponse;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class TradeOrderPaidDeadLetterReplayService {
    public static final String DEAD_LETTER_QUEUE = TradeOrderPaidListener.DEAD_LETTER_QUEUE;
    public static final String EVENT_EXCHANGE = TradeOrderPaidListener.EVENT_EXCHANGE;
    public static final String ROUTING_KEY = TradeOrderPaidListener.ROUTING_KEY;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Duration confirmTimeout;

    public TradeOrderPaidDeadLetterReplayService(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                                                  @Value("${luckyhub.messaging.publisher-confirm-timeout:5s}")
                                                  Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.confirmTimeout = confirmTimeout;
    }

    public synchronized int replay(int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("replay limit must be between 1 and 500");
        }
        int replayed = 0;
        while (replayed < limit) {
            Boolean didReplay = rabbitTemplate.execute(channel -> {
                GetResponse delivery = channel.basicGet(DEAD_LETTER_QUEUE, false);
                if (delivery == null) return false;

                long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                try {
                    OrderPaidMessage event = objectMapper.readValue(
                            new String(delivery.getBody(), StandardCharsets.UTF_8), OrderPaidMessage.class);
                    publishAndConfirm(event);
                    channel.basicAck(deliveryTag, false);
                    return true;
                } catch (Exception failure) {
                    try {
                        channel.basicNack(deliveryTag, false, true);
                    } catch (Exception nackFailure) {
                        failure.addSuppressed(nackFailure);
                    }
                    if (failure instanceof RuntimeException runtime) throw runtime;
                    throw new IllegalStateException("could not replay paid-order logistics event", failure);
                }
            });
            if (!Boolean.TRUE.equals(didReplay)) break;
            replayed++;
        }
        return replayed;
    }

    private void publishAndConfirm(OrderPaidMessage event) {
        CorrelationData correlation = new CorrelationData(event.eventId().toString());
        rabbitTemplate.convertAndSend(EVENT_EXCHANGE, ROUTING_KEY, event, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (correlation.getReturned() != null) {
                throw new AmqpException("replayed paid-order event was returned as unroutable");
            }
            if (!confirm.ack()) {
                throw new AmqpException("RabbitMQ NACK for replayed paid-order event: " + confirm.reason());
            }
        } catch (TimeoutException exception) {
            throw new AmqpException("RabbitMQ confirm timed out while replaying paid-order event", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("interrupted while replaying paid-order event", exception);
        } catch (ExecutionException exception) {
            throw new AmqpException("RabbitMQ confirm failed while replaying paid-order event", exception);
        }
    }
}
