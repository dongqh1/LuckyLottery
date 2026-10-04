package com.dongqh.lotteryservice.reconciliation.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "luckyhub.lottery", name = "reconciliation-enabled", havingValue = "true")
public class LotteryReconciliationConfiguration {
}
