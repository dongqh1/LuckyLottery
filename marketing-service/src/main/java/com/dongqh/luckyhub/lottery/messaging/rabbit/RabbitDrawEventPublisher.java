package com.dongqh.luckyhub.lottery.messaging.rabbit;

import com.dongqh.luckyhub.lottery.config.MessagingProperties;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.port.DrawEventPublisher;
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
public class RabbitDrawEventPublisher implements DrawEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public RabbitDrawEventPublisher(
            RabbitTemplate rabbitTemplate,
            MessagingProperties properties) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate must not be null");
        this.confirmTimeout = Objects.requireNonNull(
                properties, "properties must not be null").publisherConfirmTimeout();
    }

    @Override
    public void publish(DrawEventEnvelope event) {
        Objects.requireNonNull(event, "event must not be null");
        CorrelationData correlation = new CorrelationData(event.eventId().toString());
        rabbitTemplate.convertAndSend(
                LotteryRabbitTopology.EVENT_EXCHANGE,
                LotteryRabbitTopology.routingKey(event.eventType()),
                event,
                correlation);

        CorrelationData.Confirm confirm = awaitConfirm(event, correlation);
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new AmqpException("RabbitMQ returned unroutable lottery event "
                    + event.eventId() + ": " + returned.getReplyText());
        }
        if (!confirm.ack()) {
            String reason = confirm.reason() == null ? "unknown reason" : confirm.reason();
            throw new AmqpException("RabbitMQ NACK for lottery event "
                    + event.eventId() + ": " + reason);
        }
    }

    private CorrelationData.Confirm awaitConfirm(
            DrawEventEnvelope event,
            CorrelationData correlation) {
        try {
            return correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new AmqpException("RabbitMQ confirm timed out for lottery event "
                    + event.eventId(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for RabbitMQ confirm for lottery event "
                    + event.eventId(), exception);
        } catch (ExecutionException exception) {
            throw new AmqpException("RabbitMQ confirm failed for lottery event "
                    + event.eventId(), exception.getCause());
        }
    }
}
