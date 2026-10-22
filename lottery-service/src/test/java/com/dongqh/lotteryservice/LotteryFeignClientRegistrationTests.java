package com.dongqh.lotteryservice;

import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmapi.client.LogisticsClient;
import com.dongqh.hmapi.client.PointsClient;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.EnableFeignClients;

import static org.assertj.core.api.Assertions.assertThat;

class LotteryFeignClientRegistrationTests {

    @Test
    void registersClientsRequiredForLotteryRewardsAndShipping() {
        EnableFeignClients registration = LotteryServiceApplication.class
                .getAnnotation(EnableFeignClients.class);

        assertThat(registration.clients()).contains(UserClient.class);
        assertThat(registration.clients()).contains(LogisticsClient.class, PointsClient.class);
    }
}
