package com.dongqh.pointsservice;

import com.dongqh.hmapi.client.FulfillmentClient;
import com.dongqh.hmapi.client.ItemClient;
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

@MapperScan("com.dongqh.luckyhub.points.mapper")
@EnableFeignClients(clients = {ItemClient.class, UserClient.class, FulfillmentClient.class})
@Import(PermissionMvcConfiguration.class)
@SpringBootApplication(scanBasePackages = {"com.dongqh.pointsservice", "com.dongqh.luckyhub.points"})
public class PointsServiceApplication {
    @Bean
    PermissionLookup permissionLookup(UserClient client) {
        return new RequestPermissionLookup(client::getPermissionCodes);
    }

    public static void main(String[] args) {
        SpringApplication.run(PointsServiceApplication.class, args);
    }
}
