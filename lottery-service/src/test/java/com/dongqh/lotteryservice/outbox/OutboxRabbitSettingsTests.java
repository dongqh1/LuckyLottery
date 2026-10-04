package com.dongqh.lotteryservice.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRabbitSettingsTests {
    @Test
    void preservesPublisherConfirmsReturnsMandatoryAndExplicitDefaultOff() throws IOException {
        PropertySource<?> config = new YamlPropertySourceLoader()
                .load("lottery", new ClassPathResource("application.yml")).get(0);
        assertThat(config.getProperty("spring.rabbitmq.publisher-confirm-type")).isEqualTo("correlated");
        assertThat(config.getProperty("spring.rabbitmq.publisher-returns")).isEqualTo(true);
        assertThat(config.getProperty("spring.rabbitmq.template.mandatory")).isEqualTo(true);
        assertThat(config.getProperty("luckyhub.lottery.outbox.enabled"))
                .isEqualTo("${LOTTERY_OUTBOX_ENABLED:false}");
    }
}
