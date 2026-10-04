package com.dongqh.tradeservice.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PayMessagingConfiguration {
    public static final String EXCHANGE = "pay.direct";
    public static final String ROUTING_KEY = "pay.success";
    public static final String MEMBERSHIP_ROUTING_KEY = "pay.membership.success";
    public static final String QUEUE = "trade.pay.success.queue";
    public static final String MEMBERSHIP_QUEUE = "trade.membership.pay.success.queue";
    public static final String DLX = "pay.dlx";
    public static final String DLQ = "trade.pay.success.dlq";
    public static final String MEMBERSHIP_DLQ = "trade.membership.pay.success.dlq";

    @Bean
    public Declarables tradePayMessagingDeclarables() {
        DirectExchange exchange = new DirectExchange(EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(DLX, true, false);
        Queue queue = QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).build();
        Queue membershipQueue = QueueBuilder.durable(MEMBERSHIP_QUEUE).deadLetterExchange(DLX).build();
        Queue dlq = QueueBuilder.durable(DLQ).build();
        Queue membershipDlq = QueueBuilder.durable(MEMBERSHIP_DLQ).build();
        return new Declarables(
                exchange,
                dlx,
                queue,
                membershipQueue,
                dlq,
                membershipDlq,
                BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY),
                BindingBuilder.bind(dlq).to(dlx).with(ROUTING_KEY),
                BindingBuilder.bind(membershipQueue).to(exchange).with(MEMBERSHIP_ROUTING_KEY),
                BindingBuilder.bind(membershipDlq).to(dlx).with(MEMBERSHIP_ROUTING_KEY));
    }
}
