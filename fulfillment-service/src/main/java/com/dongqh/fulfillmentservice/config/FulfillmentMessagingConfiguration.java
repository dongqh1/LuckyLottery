package com.dongqh.fulfillmentservice.config;

import com.dongqh.fulfillmentservice.messaging.FulfillmentRabbitTopology;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FulfillmentMessagingConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Declarables fulfillmentRabbitDeclarables() {
        DirectExchange exchange = new DirectExchange(FulfillmentRabbitTopology.EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(
                FulfillmentRabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
        Queue executeQueue = QueueBuilder.durable(FulfillmentRabbitTopology.EXECUTE_QUEUE)
                .deadLetterExchange(FulfillmentRabbitTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(FulfillmentRabbitTopology.ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(FulfillmentRabbitTopology.DEAD_LETTER_QUEUE).build();

        return new Declarables(
                exchange,
                deadLetterExchange,
                executeQueue,
                deadLetterQueue,
                BindingBuilder.bind(executeQueue).to(exchange).with(FulfillmentRabbitTopology.ROUTING_KEY),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(FulfillmentRabbitTopology.ROUTING_KEY));
    }
}
