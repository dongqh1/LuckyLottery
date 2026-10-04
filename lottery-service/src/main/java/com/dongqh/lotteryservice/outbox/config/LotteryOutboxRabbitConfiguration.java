package com.dongqh.lotteryservice.outbox.config;

import com.dongqh.lotteryservice.outbox.publisher.LotteryRabbitTopology;
import com.dongqh.hmapi.messaging.FulfillmentStatusRabbitTopology;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LotteryOutboxRabbitConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "luckyhub.lottery.outbox", name = "enabled", havingValue = "true")
    public MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "luckyhub.lottery.outbox", name = "enabled", havingValue = "true")
    public Declarables lotteryRabbitDeclarables() {
        TopicExchange eventExchange = new TopicExchange(LotteryRabbitTopology.EVENT_EXCHANGE, true, false);
        TopicExchange deadLetterExchange = new TopicExchange(LotteryRabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
        Queue eventQueue = QueueBuilder.durable(LotteryRabbitTopology.EVENT_QUEUE)
                .deadLetterExchange(LotteryRabbitTopology.DEAD_LETTER_EXCHANGE).build();
        Queue deadLetterQueue = QueueBuilder.durable(LotteryRabbitTopology.DEAD_LETTER_QUEUE).build();
        DirectExchange fulfillmentExchange = new DirectExchange(FulfillmentStatusRabbitTopology.EXCHANGE, true, false);
        DirectExchange fulfillmentDeadLetterExchange = new DirectExchange(
                FulfillmentStatusRabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
        Queue fulfillmentStatusQueue = QueueBuilder.durable(FulfillmentStatusRabbitTopology.QUEUE)
                .deadLetterExchange(FulfillmentStatusRabbitTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(FulfillmentStatusRabbitTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
        Queue fulfillmentStatusDeadLetterQueue = QueueBuilder.durable(
                FulfillmentStatusRabbitTopology.DEAD_LETTER_QUEUE).build();
        return new Declarables(eventExchange, deadLetterExchange, eventQueue, deadLetterQueue,
                fulfillmentExchange, fulfillmentDeadLetterExchange,
                fulfillmentStatusQueue, fulfillmentStatusDeadLetterQueue,
                BindingBuilder.bind(eventQueue).to(eventExchange).with(LotteryRabbitTopology.DRAW_CONFIRMED_KEY),
                BindingBuilder.bind(eventQueue).to(eventExchange).with(LotteryRabbitTopology.DRAW_RELEASE_REQUESTED_KEY),
                BindingBuilder.bind(eventQueue).to(eventExchange).with(LotteryRabbitTopology.PRIZE_FULFILLMENT_REQUESTED_KEY),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("#"),
                BindingBuilder.bind(fulfillmentStatusQueue).to(fulfillmentExchange)
                        .with(FulfillmentStatusRabbitTopology.ROUTING_KEY),
                BindingBuilder.bind(fulfillmentStatusDeadLetterQueue).to(fulfillmentDeadLetterExchange)
                        .with(FulfillmentStatusRabbitTopology.DEAD_LETTER_ROUTING_KEY));
    }
}
