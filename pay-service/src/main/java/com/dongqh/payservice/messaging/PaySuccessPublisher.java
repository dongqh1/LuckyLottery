package com.dongqh.payservice.messaging;

import com.dongqh.hmapi.dto.PaySuccessMessage;
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
public class PaySuccessPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public PaySuccessPublisher(RabbitTemplate rabbitTemplate,
                               @Value("${hm.messaging.publisher-confirm-timeout:5s}")
                               Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    public void publish(PaySuccessMessage event) {
        CorrelationData correlation = new CorrelationData(event.eventId());
        rabbitTemplate.convertAndSend(PayMessaging.EVENT_EXCHANGE,
                PayMessaging.PAY_SUCCESS_KEY, event, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            ReturnedMessage returned = correlation.getReturned();
            if (returned != null) {
                throw new AmqpException("RabbitMQ returned unroutable payment-success event "
                        + event.eventId());
            }
            if (!confirm.ack()) {
                throw new AmqpException("RabbitMQ NACK for payment-success event "
                        + event.eventId() + ": " + confirm.reason());
            }
        } catch (TimeoutException exception) {
            throw new AmqpException("RabbitMQ confirm timed out for payment-success event "
                    + event.eventId(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for RabbitMQ confirm", exception);
        } catch (ExecutionException exception) {
            throw new AmqpException("RabbitMQ confirm failed for payment-success event "
                    + event.eventId(), exception);
        }
    }
}
