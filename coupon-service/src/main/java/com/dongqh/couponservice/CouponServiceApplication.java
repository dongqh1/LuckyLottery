package com.dongqh.couponservice;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.client.UserClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.dongqh.luckyhub.mapper.coupon")
@EnableFeignClients(clients = {ItemClient.class, MembershipClient.class, UserClient.class})
@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "com.dongqh.couponservice",
        "com.dongqh.luckyhub.coupon",
        "com.dongqh.luckyhub.controller.coupon",
        "com.dongqh.luckyhub.service.coupon"
})
public class CouponServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CouponServiceApplication.class, args);
    }
}
