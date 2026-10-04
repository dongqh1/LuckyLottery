package com.dongqh.hmsecurity.permission;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PermissionMvcConfiguration implements WebMvcConfigurer {
    private final PermissionInterceptor interceptor;

    public PermissionMvcConfiguration(@Lazy PermissionLookup permissionLookup) {
        this.interceptor = new PermissionInterceptor(permissionLookup);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/admin/**", "/api/lottery/**", "/api/benefits/**",
                        "/api/products/**", "/api/points/**", "/api/coupons/**",
                        "/api/memberships/**", "/api/orders/**", "/api/payments/**", "/api/shipping/**")
                .order(100);
    }
}
