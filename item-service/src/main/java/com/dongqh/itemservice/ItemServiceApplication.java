package com.dongqh.itemservice;

import com.dongqh.hmapi.config.DefaultFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "com.dongqh.hmapi.client", defaultConfiguration = DefaultFeignConfig.class)
@MapperScan({"com.dongqh.itemservice.mapper", "com.dongqh.luckyhub.catalog.mapper",
        "com.dongqh.luckyhub.inventory.channel.mapper"})
@SpringBootApplication(scanBasePackages = {"com.dongqh.itemservice", "com.dongqh.luckyhub.catalog",
        "com.dongqh.luckyhub.inventory.channel"})
public class ItemServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItemServiceApplication.class, args);
    }

}
