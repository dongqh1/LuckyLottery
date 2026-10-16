package com.dongqh.fulfillmentservice.config.shipping;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LogisticsCallbackProperties.class)
public class LogisticsCallbackConfiguration {
}
