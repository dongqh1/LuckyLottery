package com.dongqh.lotteryservice;

import com.dongqh.lotteryservice.config.LotteryProperties;
import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.LogisticsClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.client.PointsClient;
import com.dongqh.hmapi.client.UserClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.dongqh.lotteryservice")
@EnableFeignClients(clients = {ItemClient.class, CouponClient.class, MembershipClient.class,
        LogisticsClient.class, PointsClient.class, UserClient.class})
@EnableConfigurationProperties(LotteryProperties.class)
public class LotteryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LotteryServiceApplication.class, args);
    }
}
