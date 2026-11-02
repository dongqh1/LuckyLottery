package com.dongqh.lottery.outbox.config;

import com.dongqh.lottery.outbox.mapper.MessageOutboxMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = MessageOutboxMapper.class)
public class OutboxMapperConfiguration {
}
