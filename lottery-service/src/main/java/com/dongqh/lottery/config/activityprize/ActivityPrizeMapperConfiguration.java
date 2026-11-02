package com.dongqh.lottery.config.activityprize;

import com.dongqh.lottery.mapper.activityprize.ActivityMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = ActivityMapper.class)
public class ActivityPrizeMapperConfiguration {
}
