package com.dongqh.coupon.messaging;

import com.dongqh.coupon.config.CouponMessagingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class CouponMessagingConfigurationTests {

    @Test
    void preservesExistingPaySuccessQueueAndDeadLetterRoute() {
        Declarables declarables = new CouponMessagingConfiguration().couponPaySuccessDeclarables();
        Collection<Declarable> all = declarables.getDeclarables();

        assertThat(all.stream().filter(Queue.class::isInstance).map(Queue.class::cast)
                .map(Queue::getName)).contains(
                "marketing.pay.coupon-use.queue", "marketing.pay.coupon-use.dlq");
        assertThat(all.stream().filter(Exchange.class::isInstance).map(Exchange.class::cast)
                .map(Exchange::getName)).contains("pay.direct", "pay.dlx");
        assertThat(all.stream().filter(Binding.class::isInstance).map(Binding.class::cast)
                .map(Binding::getRoutingKey)).contains("pay.success");
    }
}
