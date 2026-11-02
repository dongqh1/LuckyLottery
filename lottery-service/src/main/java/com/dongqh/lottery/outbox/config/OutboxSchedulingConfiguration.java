package com.dongqh.lottery.outbox.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "luckyhub.lottery.outbox", name = "enabled", havingValue = "true")
public class OutboxSchedulingConfiguration {
}
