package com.dongqh.lottery.config.draworder;

import com.dongqh.lottery.mapper.draworder.DrawOrderMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = DrawOrderMapper.class)
public class DrawOrderMapperConfiguration {
}
