package com.dongqh.coupon.config;

import com.dongqh.coupon.messaging.CouponMessagingTopology;
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
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true")
public class CouponMessagingConfiguration {

    @Bean
    public MessageConverter couponRabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Declarables couponPaySuccessDeclarables() {
        DirectExchange exchange = new DirectExchange(CouponMessagingTopology.EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(
                CouponMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(CouponMessagingTopology.QUEUE)
                .deadLetterExchange(CouponMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(CouponMessagingTopology.ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(CouponMessagingTopology.DEAD_LETTER_QUEUE).build();

        return new Declarables(
                exchange,
                deadLetterExchange,
                queue,
                deadLetterQueue,
                BindingBuilder.bind(queue).to(exchange).with(CouponMessagingTopology.ROUTING_KEY),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange)
                        .with(CouponMessagingTopology.ROUTING_KEY));
    }
}
