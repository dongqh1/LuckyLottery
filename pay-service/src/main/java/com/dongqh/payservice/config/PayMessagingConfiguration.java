package com.dongqh.payservice.config;

import com.dongqh.payservice.messaging.PayMessaging;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PayMessagingConfiguration {
    @Bean
    public Declarables payEventTopology() {
        DirectExchange exchange = new DirectExchange(PayMessaging.EVENT_EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(PayMessaging.DEAD_LETTER_EXCHANGE, true, false);
        Queue membership = QueueBuilder.durable(PayMessaging.MEMBERSHIP_QUEUE)
                .deadLetterExchange(PayMessaging.DEAD_LETTER_EXCHANGE).build();
        Queue membershipDlq = QueueBuilder.durable(PayMessaging.MEMBERSHIP_DLQ).build();
        Queue tradeMembership = QueueBuilder.durable(PayMessaging.TRADE_MEMBERSHIP_QUEUE)
                .deadLetterExchange(PayMessaging.DEAD_LETTER_EXCHANGE).build();
        Queue tradeMembershipDlq = QueueBuilder.durable(PayMessaging.TRADE_MEMBERSHIP_DLQ).build();
        return new Declarables(exchange, dlx, membership, membershipDlq, tradeMembership, tradeMembershipDlq,
                BindingBuilder.bind(membership).to(exchange).with(PayMessaging.MEMBERSHIP_PAY_SUCCESS_KEY),
                BindingBuilder.bind(membershipDlq).to(dlx).with(PayMessaging.MEMBERSHIP_PAY_SUCCESS_KEY),
                BindingBuilder.bind(tradeMembership).to(exchange).with(PayMessaging.MEMBERSHIP_PAY_SUCCESS_KEY),
                BindingBuilder.bind(tradeMembershipDlq).to(dlx).with(PayMessaging.MEMBERSHIP_PAY_SUCCESS_KEY));
    }
}
