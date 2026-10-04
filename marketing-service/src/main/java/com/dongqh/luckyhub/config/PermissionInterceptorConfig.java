package com.dongqh.luckyhub.config;

import com.dongqh.hmsecurity.permission.PermissionLookup;
import com.dongqh.luckyhub.rbac.interceptor.PermissionInterceptor;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PermissionInterceptorConfig
        implements WebMvcConfigurer {

    @Bean
    public com.dongqh.hmsecurity.permission.PermissionInterceptor hmSecurityPermissionInterceptor(
            PermissionLookup permissionLookup
    ) {
        return new com.dongqh.hmsecurity.permission.PermissionInterceptor(permissionLookup);
    }

    private final PermissionInterceptor
            permissionInterceptor;

    public PermissionInterceptorConfig(
            @Lazy PermissionInterceptor permissionInterceptor
    ) {
        this.permissionInterceptor =
                permissionInterceptor;
    }

    @Override
    public void addInterceptors(
            InterceptorRegistry registry
    ) {
        registry.addInterceptor(
                        permissionInterceptor
                )
                .addPathPatterns(
                        "/api/admin/**",
                        "/api/lottery/**",
                        "/api/benefits/**",
                        "/api/products/**",
                        "/api/points/**",
                        "/api/coupons/**",
                        "/api/memberships/**",
                        "/api/orders/**",
                        "/api/payments/**",
                        "/api/shipping/**"
                )
                .order(100);
    }
}
