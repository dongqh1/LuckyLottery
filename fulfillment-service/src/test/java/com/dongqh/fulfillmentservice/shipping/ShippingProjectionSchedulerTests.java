package com.dongqh.fulfillmentservice.shipping;

import com.dongqh.fulfillmentservice.service.shipping.ShippingProjectionScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingProjectionSchedulerTests {

    @Test
    void usesProjectionSpecificCadenceWithMarketingCompatibleDefaults() throws Exception {
        Scheduled scheduled = ShippingProjectionScheduler.class
                .getMethod("project")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${luckyhub.fulfillment.projection-interval:5s}");
        assertThat(scheduled.initialDelayString())
                .isEqualTo("${luckyhub.fulfillment.projection-initial-delay:60s}");
    }
}
