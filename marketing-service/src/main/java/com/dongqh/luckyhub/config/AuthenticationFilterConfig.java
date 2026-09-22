package com.dongqh.luckyhub.config;

import com.dongqh.luckyhub.auth.filter.AuthenticationFilter;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import com.dongqh.luckyhub.auth.service.HmallUserProjectionService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthenticationFilterConfig {

    @Bean
    public FilterRegistrationBean<AuthenticationFilter>
    authenticationFilterRegistration(
            JwtService jwtService,
            HmallUserProjectionService userProjectionService,
            ObjectMapper objectMapper
    ) {
        AuthenticationFilter filter =
                new AuthenticationFilter(
                        jwtService,
                        userProjectionService,
                        objectMapper
                );

        return registration(filter);
    }

    private FilterRegistrationBean<AuthenticationFilter> registration(
            AuthenticationFilter filter
    ) {

        FilterRegistrationBean<AuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.addUrlPatterns(
                "/api/auth/me",
                "/api/auth/logout",
                "/api/admin/*",
                "/api/lottery/*",
                "/api/benefits/*",
                "/api/products/*",
                "/api/points/*",
                "/api/coupons/*",
                "/api/memberships/*",
                "/api/orders/*",
                "/api/payments/*",
                "/api/shipping/*"
        );

        registration.setOrder(20);

        return registration;
    }

    /** Compatibility overload for tests written against the LuckyHub session filter. */
    public FilterRegistrationBean<AuthenticationFilter>
    authenticationFilterRegistration(
            JwtService jwtService,
            SessionService ignoredSessionService,
            ObjectMapper objectMapper
    ) {
        return registration(new AuthenticationFilter(jwtService, objectMapper));
    }
}
