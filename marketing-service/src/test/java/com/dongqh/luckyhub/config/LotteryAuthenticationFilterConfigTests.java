package com.dongqh.luckyhub.config;

import com.dongqh.luckyhub.domain.filter.auth.AuthenticationFilter;
import com.dongqh.luckyhub.security.auth.JwtService;
import com.dongqh.luckyhub.security.auth.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LotteryAuthenticationFilterConfigTests {
    @Test
    void jwtFilterProtectsUnifiedLotteryAndBenefitApis() {
        FilterRegistrationBean<AuthenticationFilter> registration = new AuthenticationFilterConfig()
                .authenticationFilterRegistration(mock(JwtService.class), mock(SessionService.class), new ObjectMapper());

        assertThat(registration.getUrlPatterns()).contains(
                "/api/lottery/*", "/api/benefits/*");
    }
}
