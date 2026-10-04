package com.dongqh.membershipservice;

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

@SpringBootApplication(scanBasePackages = "com.dongqh.membershipservice")
@EnableFeignClients(clients = UserClient.class)
@Import(PermissionMvcConfiguration.class)
@MapperScan("com.dongqh.membershipservice.membership.mapper")
public class MembershipServiceApplication {
    @Bean
    PermissionLookup permissionLookup(UserClient client) {
        return new RequestPermissionLookup(client::getPermissionCodes);
    }

    public static void main(String[] args) {
        SpringApplication.run(MembershipServiceApplication.class, args);
    }
}
