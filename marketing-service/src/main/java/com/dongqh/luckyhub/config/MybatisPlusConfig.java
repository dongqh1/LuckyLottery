package com.dongqh.luckyhub.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@MapperScan(basePackages = {
        "com.dongqh.luckyhub.mapper.rbac",
        "com.dongqh.luckyhub.mapper.prize",
        "com.dongqh.luckyhub.mapper.activity",
        "com.dongqh.luckyhub.mapper.lottery",
        "com.dongqh.luckyhub.mapper.inventory",
        "com.dongqh.luckyhub.mapper.benefit",
        "com.dongqh.luckyhub.mapper.catalog",
        "com.dongqh.luckyhub.mapper.reward",
        "com.dongqh.luckyhub.domain.channel.inventory.mapper",
        "com.dongqh.luckyhub.mapper.points",
        "com.dongqh.luckyhub.mapper.coupon",
        "com.dongqh.luckyhub.mapper.membership",
        "com.dongqh.luckyhub.mapper.order",
        "com.dongqh.luckyhub.mapper.payment",
        "com.dongqh.luckyhub.mapper.fulfillment",
        "com.dongqh.luckyhub.mapper.drawchance",
        "com.dongqh.luckyhub.mapper.shipping"
})
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
