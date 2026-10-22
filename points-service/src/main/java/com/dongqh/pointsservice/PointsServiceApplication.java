package com.dongqh.pointsservice;

import com.dongqh.hmapi.client.LogisticsClient;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.UserClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.dongqh.luckyhub.mapper.points")
@EnableFeignClients(clients = {ItemClient.class, UserClient.class, LogisticsClient.class})
@SpringBootApplication(scanBasePackages = {
        "com.dongqh.pointsservice",
        "com.dongqh.luckyhub.points",
        "com.dongqh.luckyhub.service.points"
})
public class PointsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PointsServiceApplication.class, args);
    }
}
