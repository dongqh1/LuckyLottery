package com.dongqh.luckyhub.lottery.config;

import com.dongqh.luckyhub.lottery.messaging.rabbit.LotteryRabbitTopology;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MessagingProperties.class)
public class LotteryMessagingConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "luckyhub.messaging",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "luckyhub.messaging",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public Declarables lotteryRabbitDeclarables() {
        TopicExchange eventExchange = new TopicExchange(
                LotteryRabbitTopology.EVENT_EXCHANGE, true, false);
        TopicExchange deadLetterExchange = new TopicExchange(
                LotteryRabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
        Queue eventQueue = QueueBuilder.durable(LotteryRabbitTopology.EVENT_QUEUE)
                .deadLetterExchange(LotteryRabbitTopology.DEAD_LETTER_EXCHANGE)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(LotteryRabbitTopology.DEAD_LETTER_QUEUE)
                .build();

        return new Declarables(
                eventExchange,
                deadLetterExchange,
                eventQueue,
                deadLetterQueue,
                BindingBuilder.bind(eventQueue).to(eventExchange)
                        .with(LotteryRabbitTopology.DRAW_CONFIRMED_KEY),
                BindingBuilder.bind(eventQueue).to(eventExchange)
                        .with(LotteryRabbitTopology.DRAW_RELEASE_REQUESTED_KEY),
                BindingBuilder.bind(eventQueue).to(eventExchange)
                        .with(LotteryRabbitTopology.PRIZE_FULFILLMENT_REQUESTED_KEY),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("#"));
    }
}
