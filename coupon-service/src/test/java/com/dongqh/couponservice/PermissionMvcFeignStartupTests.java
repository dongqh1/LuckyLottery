package com.dongqh.couponservice;

import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmsecurity.permission.PermissionLookup;
import com.dongqh.hmsecurity.permission.PermissionMvcConfiguration;
import com.dongqh.hmsecurity.permission.RequestPermissionLookup;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionMvcFeignStartupTests {
    private final WebApplicationContextRunner context = new WebApplicationContextRunner()
            .withPropertyValues("spring.cloud.openfeign.client.config.user-service.url=http://localhost:8083")
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void registersPermissionInterceptorWhenPermissionLookupUsesFeign() {
        context.run(applicationContext -> {
            assertThat(applicationContext).hasNotFailed();
            assertThat(applicationContext.getBeansOfType(PermissionLookup.class)).hasSize(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableFeignClients(clients = UserClient.class)
    @Import({PermissionMvcConfiguration.class, FeignAutoConfiguration.class})
    static class TestConfiguration {
        @Bean
        PermissionLookup permissionLookup(UserClient client) {
            return new RequestPermissionLookup(client::getPermissionCodes);
        }
    }
}
