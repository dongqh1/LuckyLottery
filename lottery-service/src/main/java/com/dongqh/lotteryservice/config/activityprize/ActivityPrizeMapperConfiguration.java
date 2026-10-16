package com.dongqh.lotteryservice.config.activityprize;

import com.dongqh.lotteryservice.mapper.activityprize.ActivityMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = ActivityMapper.class)
public class ActivityPrizeMapperConfiguration {
}
