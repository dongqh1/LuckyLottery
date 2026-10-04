package com.dongqh.lotteryservice;

import com.dongqh.lotteryservice.config.LotteryProperties;
import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.client.FulfillmentClient;
import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmsecurity.permission.PermissionLookup;
import com.dongqh.hmsecurity.permission.PermissionMvcConfiguration;
import com.dongqh.hmsecurity.permission.RequestPermissionLookup;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.dongqh.lotteryservice")
@EnableFeignClients(clients = {ItemClient.class, CouponClient.class, MembershipClient.class,
        FulfillmentClient.class, UserClient.class})
@EnableConfigurationProperties(LotteryProperties.class)
@Import(PermissionMvcConfiguration.class)
public class LotteryServiceApplication {
    @Bean
    PermissionLookup permissionLookup(UserClient client) {
        return new RequestPermissionLookup(client::getPermissionCodes);
    }

    public static void main(String[] args) {
        SpringApplication.run(LotteryServiceApplication.class, args);
    }
}
