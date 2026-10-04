package com.dongqh.fulfillmentservice;

import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.client.PointsClient;
import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmsecurity.permission.PermissionLookup;
import com.dongqh.hmsecurity.permission.RequestPermissionLookup;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.dongqh.fulfillmentservice")
@EnableFeignClients(clients = {CouponClient.class, PointsClient.class, MembershipClient.class,
        UserClient.class})
@EnableScheduling
public class FulfillmentServiceApplication {
    @Bean
    PermissionLookup permissionLookup(UserClient client) {
        return new RequestPermissionLookup(client::getPermissionCodes);
    }

    public static void main(String[] args) {
        SpringApplication.run(FulfillmentServiceApplication.class, args);
    }
}
