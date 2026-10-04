package com.dongqh.luckyhub.config;

import com.dongqh.hmapi.client.MarketingPermissionClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class MarketingServiceDiscoveryNameTests {

    @Test
    void discoveryNameMatchesTheMarketingPermissionFeignClient() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        yaml.afterPropertiesSet();

        FeignClient permissionClient = MarketingPermissionClient.class.getAnnotation(FeignClient.class);
        assertThat(yaml.getObject()).isNotNull();
        assertThat(yaml.getObject().getProperty("spring.application.name"))
                .isEqualTo(permissionClient.value())
                .isEqualTo("marketing-service");
    }
}
