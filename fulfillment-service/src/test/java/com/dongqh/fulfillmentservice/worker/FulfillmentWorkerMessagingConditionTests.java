package com.dongqh.fulfillmentservice.worker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentWorkerMessagingConditionTests {

    @Test
    void workerRunsOnlyWhenMessagingIsEnabledAndDefaultsToEnabled() {
        assertMessagingEnabledCondition(FulfillmentWorker.class);
    }

    @Test
    void schedulerRunsOnlyWhenMessagingIsEnabledAndDefaultsToEnabled() {
        assertMessagingEnabledCondition(FulfillmentScheduler.class);
    }

    private void assertMessagingEnabledCondition(Class<?> componentType) {
        ConditionalOnProperty condition = componentType.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition)
                .as("%s should be conditional on luckyhub.messaging.enabled", componentType.getSimpleName())
                .isNotNull();
        assertThat(condition.prefix()).isEqualTo("luckyhub.messaging");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isTrue();
    }
}
