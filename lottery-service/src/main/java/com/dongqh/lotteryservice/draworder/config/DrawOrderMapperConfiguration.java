package com.dongqh.lotteryservice.draworder.config;

import com.dongqh.lotteryservice.draworder.mapper.DrawOrderMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = DrawOrderMapper.class)
public class DrawOrderMapperConfiguration {
}
