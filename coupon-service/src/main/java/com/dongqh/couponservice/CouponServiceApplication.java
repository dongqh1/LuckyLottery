package com.dongqh.couponservice;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmsecurity.permission.PermissionLookup;
import com.dongqh.hmsecurity.permission.PermissionMvcConfiguration;
import com.dongqh.hmsecurity.permission.RequestPermissionLookup;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.dongqh.luckyhub.coupon.mapper")
@EnableFeignClients(clients = {ItemClient.class, UserClient.class, MembershipClient.class})
@EnableScheduling
@Import(PermissionMvcConfiguration.class)
@SpringBootApplication(scanBasePackages = {
        "com.dongqh.couponservice",
        "com.dongqh.luckyhub.coupon"
})
public class CouponServiceApplication {
    @Bean
    PermissionLookup permissionLookup(UserClient client) {
        return new RequestPermissionLookup(client::getPermissionCodes);
    }

    public static void main(String[] args) {
        SpringApplication.run(CouponServiceApplication.class, args);
    }
}
