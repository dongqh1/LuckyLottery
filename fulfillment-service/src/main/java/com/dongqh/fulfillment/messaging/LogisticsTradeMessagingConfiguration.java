package com.dongqh.fulfillment.messaging;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogisticsTradeMessagingConfiguration {
    @Bean
    Declarables logisticsTradeDeadLetterTopology() {
        TopicExchange deadLetterExchange = new TopicExchange(TradeOrderPaidListener.DEAD_LETTER_EXCHANGE,
                true, false);
        var deadLetterQueue = QueueBuilder.durable(TradeOrderPaidListener.DEAD_LETTER_QUEUE).build();
        var binding = BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange)
                .with(TradeOrderPaidListener.ROUTING_KEY);
        return new Declarables(deadLetterExchange, deadLetterQueue, binding);
    }
}
