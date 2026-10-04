package com.dongqh.lotteryservice.activityprize.config;

import com.dongqh.lotteryservice.activityprize.mapper.ActivityMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = ActivityMapper.class)
public class ActivityPrizeMapperConfiguration {
}
