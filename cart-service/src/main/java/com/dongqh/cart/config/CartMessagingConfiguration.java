package com.dongqh.cart.config;

import com.dongqh.cart.listener.OrderCreatedListener;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CartMessagingConfiguration {
    @Bean
    Declarables cartDeadLetterTopology() {
        TopicExchange dlx = new TopicExchange(OrderCreatedListener.DLX, true, false);
        var dlq = QueueBuilder.durable("cart.trade.order-created.dlq").build();
        return new Declarables(dlx, dlq, BindingBuilder.bind(dlq).to(dlx).with("#"));
    }
}
