package com.dongqh.lottery.config.drawchance;

import com.dongqh.lottery.mapper.drawchance.DrawChanceAccountMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = DrawChanceAccountMapper.class)
public class DrawChanceMapperConfiguration {
}
