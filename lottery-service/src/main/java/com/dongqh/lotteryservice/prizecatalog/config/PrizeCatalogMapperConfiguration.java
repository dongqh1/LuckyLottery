package com.dongqh.lotteryservice.prizecatalog.config;

import com.dongqh.lotteryservice.prizecatalog.mapper.PrizeCatalogMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = PrizeCatalogMapper.class)
public class PrizeCatalogMapperConfiguration {
}
