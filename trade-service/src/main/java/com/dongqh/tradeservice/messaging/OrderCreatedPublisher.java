package com.dongqh.tradeservice.messaging;

import com.dongqh.hmapi.dto.OrderCreatedMessage;
import com.dongqh.hmapi.dto.OrderPaidMessage;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OrderCreatedPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public OrderCreatedPublisher(RabbitTemplate rabbitTemplate,
                                 @Value("${hm.messaging.publisher-confirm-timeout:5s}") Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    public void publish(OrderCreatedMessage event) {
        publish(event.eventId().toString(), OrderMessaging.ORDER_CREATED_KEY, event);
    }

    public void publish(OrderPaidMessage event) {
        publish(event.eventId().toString(), OrderMessaging.ORDER_PAID_KEY, event);
    }

    private void publish(String eventId, String routingKey, Object event) {
        CorrelationData correlation = new CorrelationData(eventId);
        rabbitTemplate.convertAndSend(OrderMessaging.EVENT_EXCHANGE,
                routingKey, event, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            ReturnedMessage returned = correlation.getReturned();
            if (returned != null) {
                throw new AmqpException("RabbitMQ returned unroutable trade event " + eventId);
            }
            if (!confirm.ack()) {
                throw new AmqpException("RabbitMQ NACK for trade event " + eventId
                        + ": " + confirm.reason());
            }
        } catch (TimeoutException exception) {
            throw new AmqpException("RabbitMQ confirm timed out for trade event " + eventId, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for RabbitMQ confirm", exception);
        } catch (ExecutionException exception) {
            throw new AmqpException("RabbitMQ confirm failed for trade event " + eventId, exception);
        }
    }
}
