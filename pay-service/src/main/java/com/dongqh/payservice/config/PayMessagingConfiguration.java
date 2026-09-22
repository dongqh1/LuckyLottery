package com.dongqh.payservice.config;

import com.dongqh.payservice.messaging.PayMessaging;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PayMessagingConfiguration {
    @Bean
    public DirectExchange payEventExchange() {
        return new DirectExchange(PayMessaging.EVENT_EXCHANGE, true, false);
    }
}
