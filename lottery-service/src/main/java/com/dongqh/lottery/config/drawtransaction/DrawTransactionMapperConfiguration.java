package com.dongqh.lottery.config.drawtransaction;

import com.dongqh.lottery.mapper.drawtransaction.DrawRecordMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = DrawRecordMapper.class)
public class DrawTransactionMapperConfiguration {
}
