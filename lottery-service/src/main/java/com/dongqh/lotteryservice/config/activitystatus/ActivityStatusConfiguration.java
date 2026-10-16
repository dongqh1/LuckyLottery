package com.dongqh.lotteryservice.config.activitystatus;

import com.dongqh.lotteryservice.mapper.activitystatus.ActivityStatusMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = ActivityStatusMapper.class)
public class ActivityStatusConfiguration {

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "luckyhub.lottery.activity-status", name = "enabled", havingValue = "true")
    static class SchedulingConfiguration {
    }
}
