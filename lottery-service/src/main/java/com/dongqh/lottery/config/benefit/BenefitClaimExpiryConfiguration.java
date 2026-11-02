package com.dongqh.lottery.config.benefit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "luckyhub.lottery.benefit-claim-expiry", name = "enabled", havingValue = "true")
public class BenefitClaimExpiryConfiguration {
}
