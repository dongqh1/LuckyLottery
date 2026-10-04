package com.dongqh.fulfillmentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class FulfillmentTimeConfiguration {

    @Bean
    public Clock fulfillmentClock() {
        return Clock.systemDefaultZone();
    }
}
