package com.dongqh.pointsservice;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmsecurity.permission.PermissionMvcConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

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
    void enablesRequiredFeignClientsAndOnlyImportsSharedPermissionConfiguration() {
        EnableFeignClients feignClients = PointsServiceApplication.class.getAnnotation(EnableFeignClients.class);
        assertThat(feignClients).isNotNull();
        assertThat(feignClients.clients()).contains(ItemClient.class, UserClient.class);

        Import imports = PointsServiceApplication.class.getAnnotation(Import.class);
        assertThat(imports).isNotNull();
        assertThat(imports.value()).containsExactly(PermissionMvcConfiguration.class);
    }
}
