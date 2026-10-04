package com.dongqh.lotteryservice.drawchance.config;

import com.dongqh.lotteryservice.drawchance.mapper.DrawChanceAccountMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = DrawChanceAccountMapper.class)
public class DrawChanceMapperConfiguration {
}
