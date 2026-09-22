package com.dongqh.luckyhub.fulfillment.messaging.rabbit;

import com.dongqh.luckyhub.fulfillment.messaging.event.FulfillmentTaskReadyEvent;
import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitFulfillmentEventPublisher implements FulfillmentEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public RabbitFulfillmentEventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate);
        this.confirmTimeout = properties.publisherConfirmTimeout();
    }

    @Override
    public void publish(FulfillmentTaskReadyEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        CorrelationData correlation = new CorrelationData(event.eventId());
        rabbitTemplate.convertAndSend(FulfillmentRabbitTopology.EXCHANGE,
                FulfillmentRabbitTopology.ROUTING_KEY, event, correlation);
        CorrelationData.Confirm confirm = await(event, correlation);
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new AmqpException("RabbitMQ returned fulfillment event " + event.eventId());
        }
        if (!confirm.ack()) {
            throw new AmqpException("RabbitMQ NACK for fulfillment event " + event.eventId()
                    + ": " + (confirm.reason() == null ? "unknown reason" : confirm.reason()));
        }
    }

    private CorrelationData.Confirm await(FulfillmentTaskReadyEvent event, CorrelationData correlation) {
        try {
            return correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new AmqpException("RabbitMQ confirm timed out for fulfillment event " + event.eventId(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for fulfillment confirm", exception);
        } catch (ExecutionException exception) {
            throw new AmqpException("RabbitMQ confirm failed for fulfillment event " + event.eventId(), exception.getCause());
        }
    }
}
