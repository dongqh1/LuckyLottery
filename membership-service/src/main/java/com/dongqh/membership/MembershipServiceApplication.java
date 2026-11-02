package com.dongqh.membership;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.dongqh.membership")
@EnableFeignClients
@MapperScan("com.dongqh.membership.mapper.membership")
public class MembershipServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MembershipServiceApplication.class, args);
    }
}
