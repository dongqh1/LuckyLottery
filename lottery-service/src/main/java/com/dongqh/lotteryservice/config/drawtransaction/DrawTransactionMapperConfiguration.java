package com.dongqh.lotteryservice.config.drawtransaction;

import com.dongqh.lotteryservice.mapper.drawtransaction.DrawRecordMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = DrawRecordMapper.class)
public class DrawTransactionMapperConfiguration {
}
