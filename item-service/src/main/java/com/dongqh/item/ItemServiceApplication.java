package com.dongqh.item;

import com.dongqh.hmapi.config.DefaultFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "com.dongqh.hmapi.client", defaultConfiguration = DefaultFeignConfig.class)
@MapperScan({"com.dongqh.item.mapper",
        "com.dongqh.item.domain.channel.inventory.mapper"})
@SpringBootApplication
public class ItemServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItemServiceApplication.class, args);
    }

}
