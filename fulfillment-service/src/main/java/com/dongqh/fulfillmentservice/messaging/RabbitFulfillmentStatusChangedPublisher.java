package com.dongqh.fulfillmentservice.messaging;

import com.dongqh.fulfillmentservice.service.port.FulfillmentStatusChangedPublisher;
import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import com.dongqh.hmapi.messaging.FulfillmentStatusRabbitTopology;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitFulfillmentStatusChangedPublisher implements FulfillmentStatusChangedPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public RabbitFulfillmentStatusChangedPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${luckyhub.messaging.publisher-confirm-timeout:5s}") Duration confirmTimeout
    ) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate must not be null");
        if (confirmTimeout == null || confirmTimeout.isZero() || confirmTimeout.isNegative()) {
            throw new IllegalArgumentException("publisher confirm timeout must be positive");
        }
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(FulfillmentStatusChangedEventV1 event) {
        Objects.requireNonNull(event, "event must not be null");
        CorrelationData correlation = new CorrelationData(event.eventId());
        rabbitTemplate.convertAndSend(FulfillmentStatusRabbitTopology.EXCHANGE,
                FulfillmentStatusRabbitTopology.ROUTING_KEY, event, correlation);
        CorrelationData.Confirm confirm = awaitConfirm(correlation);
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new AmqpException("RabbitMQ returned fulfillment status event " + event.eventId());
        }
        if (!confirm.ack()) {
            String reason = confirm.reason() == null ? "unknown reason" : confirm.reason();
            throw new AmqpException("RabbitMQ NACK for fulfillment status event " + event.eventId() + ": " + reason);
        }
    }

    private CorrelationData.Confirm awaitConfirm(CorrelationData correlation) {
        try {
            return correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new AmqpException("RabbitMQ confirm timed out for fulfillment status event", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for fulfillment status confirm", exception);
        } catch (ExecutionException exception) {
            throw new AmqpException("RabbitMQ status confirm failed", exception.getCause());
        }
    }
}
