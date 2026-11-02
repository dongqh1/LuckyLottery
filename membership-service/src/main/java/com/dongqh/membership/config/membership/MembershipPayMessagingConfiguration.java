package com.dongqh.membership.config.membership;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "membership.messaging", name = "enabled", havingValue = "true")
public class MembershipPayMessagingConfiguration {
    public static final String EXCHANGE = "pay.direct";
    public static final String DLX = "pay.dlx";
    public static final String ROUTING_KEY = "pay.membership.success";
    public static final String QUEUE = "membership.pay.success.queue";
    public static final String DLQ = "membership.pay.success.dlq";

    @Bean
    public Declarables membershipPayTopology() {
        DirectExchange exchange = new DirectExchange(EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(DLX, true, false);
        Queue queue = QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).build();
        Queue deadLetterQueue = QueueBuilder.durable(DLQ).build();
        return new Declarables(exchange, deadLetterExchange, queue, deadLetterQueue,
                BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(ROUTING_KEY));
    }
}
