package com.dongqh.lotteryservice.outbox.publisher;

import com.dongqh.lotteryservice.outbox.event.DrawEventEnvelope;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(prefix = "luckyhub.lottery.outbox", name = "enabled", havingValue = "true")
public class RabbitDrawEventPublisher implements DrawEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    @Autowired
    public RabbitDrawEventPublisher(RabbitTemplate rabbitTemplate,
                                    @Value("${luckyhub.lottery.outbox.publisher-confirm-timeout:5s}") String confirmTimeout) {
        this(rabbitTemplate, DurationStyle.detectAndParse(confirmTimeout));
    }

    public RabbitDrawEventPublisher(RabbitTemplate rabbitTemplate, Duration confirmTimeout) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate must not be null");
        if (confirmTimeout == null || confirmTimeout.isZero() || confirmTimeout.isNegative()) {
            throw new IllegalArgumentException("confirmTimeout must be positive");
        }
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(DrawEventEnvelope event) {
        Objects.requireNonNull(event, "event must not be null");
        CorrelationData correlation = new CorrelationData(event.eventId().toString());
        rabbitTemplate.convertAndSend(LotteryRabbitTopology.EVENT_EXCHANGE,
                LotteryRabbitTopology.routingKey(event.eventType()), event, correlation);
        CorrelationData.Confirm confirm = awaitConfirm(event, correlation);
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new AmqpException("RabbitMQ returned unroutable lottery event "
                    + event.eventId() + ": " + returned.getReplyText());
        }
        if (!confirm.ack()) {
            String reason = confirm.reason() == null ? "unknown reason" : confirm.reason();
            throw new AmqpException("RabbitMQ NACK for lottery event " + event.eventId() + ": " + reason);
        }
    }

    private CorrelationData.Confirm awaitConfirm(DrawEventEnvelope event, CorrelationData correlation) {
        try {
            return correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new AmqpException("RabbitMQ confirm timed out for lottery event " + event.eventId(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for RabbitMQ confirm for lottery event " + event.eventId(), exception);
        } catch (ExecutionException exception) {
            throw new AmqpException("RabbitMQ confirm failed for lottery event " + event.eventId(), exception.getCause());
        }
    }
}
