package com.dongqh.fulfillment.messaging;

import com.dongqh.fulfillment.service.port.FulfillmentTaskReadyPublisher;
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
public class RabbitFulfillmentTaskReadyPublisher implements FulfillmentTaskReadyPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public RabbitFulfillmentTaskReadyPublisher(
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
    public void publish(FulfillmentTaskReadyEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        CorrelationData correlation = new CorrelationData(event.eventId());
        rabbitTemplate.convertAndSend(
                FulfillmentRabbitTopology.EXCHANGE,
                FulfillmentRabbitTopology.ROUTING_KEY,
                event,
                correlation);

        CorrelationData.Confirm confirm = awaitConfirm(event, correlation);
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new AmqpException("RabbitMQ returned fulfillment event " + event.eventId());
        }
        if (!confirm.ack()) {
            String reason = confirm.reason() == null ? "unknown reason" : confirm.reason();
            throw new AmqpException("RabbitMQ NACK for fulfillment event " + event.eventId() + ": " + reason);
        }
    }

    private CorrelationData.Confirm awaitConfirm(
            FulfillmentTaskReadyEvent event,
            CorrelationData correlation
    ) {
        try {
            return correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new AmqpException("RabbitMQ confirm timed out for fulfillment event " + event.eventId(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for fulfillment confirm", exception);
        } catch (ExecutionException exception) {
            throw new AmqpException(
                    "RabbitMQ confirm failed for fulfillment event " + event.eventId(), exception.getCause());
        }
    }
}
