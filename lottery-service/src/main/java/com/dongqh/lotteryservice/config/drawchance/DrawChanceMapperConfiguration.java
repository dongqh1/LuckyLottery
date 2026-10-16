package com.dongqh.lotteryservice.config.drawchance;

import com.dongqh.lotteryservice.mapper.drawchance.DrawChanceAccountMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = DrawChanceAccountMapper.class)
public class DrawChanceMapperConfiguration {
}
