package com.dongqh.trade;

import com.dongqh.hmapi.config.DefaultFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.dongqh.trade.mapper")
@EnableFeignClients(basePackages = "com.dongqh.hmapi.client",defaultConfiguration = DefaultFeignConfig.class)
@EnableScheduling
public class TradeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeServiceApplication.class, args);
    }

}
