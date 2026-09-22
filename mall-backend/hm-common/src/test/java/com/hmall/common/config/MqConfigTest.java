package com.hmall.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

class MqConfigTest {

    @Test
    void createsBoot4JacksonMessageConverter() {
        assertThat(new MqConfig().messageConverter())
                .isInstanceOf(JacksonJsonMessageConverter.class);
    }
}
