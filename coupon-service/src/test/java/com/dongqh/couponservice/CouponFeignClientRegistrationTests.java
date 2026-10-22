package com.dongqh.couponservice;

import com.dongqh.hmapi.client.UserClient;
import com.dongqh.luckyhub.controller.coupon.CouponAdminController;
import com.dongqh.luckyhub.service.coupon.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class CouponFeignClientRegistrationTests {

    @Test
    void registersUserClientRequiredByCouponIntegrationService() {
        EnableFeignClients registration = CouponServiceApplication.class
                .getAnnotation(EnableFeignClients.class);

        assertThat(registration.clients()).contains(UserClient.class);
    }

    @Test
    void scansAdminControllerPackageSoTemplateSearchRouteIsRegistered() {
        SpringBootApplication application = CouponServiceApplication.class
                .getAnnotation(SpringBootApplication.class);
        String controllerPackage = CouponAdminController.class.getPackageName();

        assertThat(application.scanBasePackages()).contains(controllerPackage, CouponService.class.getPackageName());
    }
}
