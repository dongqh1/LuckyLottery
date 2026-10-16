package com.dongqh.lotteryservice.config.prizecatalog;

import com.dongqh.lotteryservice.mapper.prizecatalog.PrizeCatalogMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = PrizeCatalogMapper.class)
public class PrizeCatalogMapperConfiguration {
}
