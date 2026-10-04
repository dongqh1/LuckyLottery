package com.dongqh.lotteryservice.drawtransaction.config;

import com.dongqh.lotteryservice.drawtransaction.mapper.DrawRecordMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = DrawRecordMapper.class)
public class DrawTransactionMapperConfiguration {
}
