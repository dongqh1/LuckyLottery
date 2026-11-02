package com.dongqh.coupon;

import com.dongqh.hmapi.client.UserClient;
import com.dongqh.coupon.controller.CouponAdminController;
import com.dongqh.coupon.service.coupon.CouponService;
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
    void scansAdminControllerAndServiceFromApplicationPackage() {
        SpringBootApplication application = CouponServiceApplication.class
                .getAnnotation(SpringBootApplication.class);
        String controllerPackage = CouponAdminController.class.getPackageName();
        String servicePackage = CouponService.class.getPackageName();
        String applicationPackage = CouponServiceApplication.class.getPackageName();

        assertThat(application.scanBasePackages()).isEmpty();
        assertThat(controllerPackage).startsWith(applicationPackage + ".");
        assertThat(servicePackage).startsWith(applicationPackage + ".");
    }
}
