package com.dongqh.pointsservice;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.LogisticsClient;
import com.dongqh.hmapi.client.UserClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import static org.assertj.core.api.Assertions.assertThat;

class PointsServiceApplicationContractTests {

    @Test
    void scansBothTheApplicationAndExtractedPointsDomainPackages() {
        SpringBootApplication application = PointsServiceApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(application).isNotNull();
        assertThat(application.scanBasePackages())
                .contains("com.dongqh.pointsservice", "com.dongqh.luckyhub.points");
    }

    @Test
    void enablesRequiredFeignClients() {
        EnableFeignClients feignClients = PointsServiceApplication.class.getAnnotation(EnableFeignClients.class);
        assertThat(feignClients).isNotNull();
        assertThat(feignClients.clients()).contains(ItemClient.class, UserClient.class, LogisticsClient.class);
    }
}
