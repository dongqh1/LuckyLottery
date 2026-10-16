package com.dongqh.luckyhub.config.fulfillment;

import com.dongqh.luckyhub.messaging.fulfillment.rabbit.FulfillmentRabbitTopology;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FulfillmentMessagingConfiguration {

    @Bean
    public Declarables fulfillmentRabbitDeclarables() {
        DirectExchange exchange = new DirectExchange(FulfillmentRabbitTopology.EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(
                FulfillmentRabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(FulfillmentRabbitTopology.EXECUTE_QUEUE)
                .deadLetterExchange(FulfillmentRabbitTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(FulfillmentRabbitTopology.ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(FulfillmentRabbitTopology.DEAD_LETTER_QUEUE).build();
        return new Declarables(
                exchange,
                deadLetterExchange,
                queue,
                deadLetterQueue,
                BindingBuilder.bind(queue).to(exchange).with(FulfillmentRabbitTopology.ROUTING_KEY),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange)
                        .with(FulfillmentRabbitTopology.ROUTING_KEY));
    }
}
