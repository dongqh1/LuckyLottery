package com.dongqh.fulfillment.shipping;

import com.dongqh.fulfillment.config.shipping.LogisticsCallbackProperties;
import com.dongqh.fulfillment.integration.crypto.shipping.LogisticsCallbackSigner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsCallbackSignerContextTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withPropertyValues(
                    "luckyhub.fulfillment.shipping-callback.secret=test-callback-secret-32-bytes-long",
                    "luckyhub.fulfillment.shipping-callback.window=PT5M")
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void selectsThePropertiesConstructorForSpringComponentCreation() {
        context.run(applicationContext -> {
            assertThat(applicationContext).hasNotFailed();
            assertThat(applicationContext).hasSingleBean(LogisticsCallbackSigner.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LogisticsCallbackProperties.class)
    @Import(LogisticsCallbackSigner.class)
    static class TestConfiguration {
    }
}
