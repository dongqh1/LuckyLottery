package com.dongqh.coupon;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.client.UserClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.dongqh.coupon.mapper")
@EnableFeignClients(clients = {ItemClient.class, MembershipClient.class, UserClient.class})
@EnableScheduling
@SpringBootApplication
public class CouponServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CouponServiceApplication.class, args);
    }
}
