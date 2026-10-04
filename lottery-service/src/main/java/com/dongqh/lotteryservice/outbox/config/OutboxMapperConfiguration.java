package com.dongqh.lotteryservice.outbox.config;

import com.dongqh.lotteryservice.outbox.mapper.MessageOutboxMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = MessageOutboxMapper.class)
public class OutboxMapperConfiguration {
}
