package com.dongqh.fulfillmentservice;

import com.dongqh.hmsecurity.permission.PermissionInterceptor;
import com.dongqh.hmsecurity.permission.PermissionLookup;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FulfillmentPermissionMvcConfiguration implements WebMvcConfigurer {
    private static final String LOGISTICS_CALLBACK_PATH = "/api/shipping/callbacks/logistics";

    private final HandlerInterceptor permissionInterceptor;

    public FulfillmentPermissionMvcConfiguration(@Lazy PermissionLookup permissionLookup) {
        PermissionInterceptor delegate = new PermissionInterceptor(permissionLookup);
        this.permissionInterceptor = new HandlerInterceptor() {
            @Override
            public boolean preHandle(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    Object handler
            ) {
                if (HttpMethod.POST.matches(request.getMethod())
                        && LOGISTICS_CALLBACK_PATH.equals(request.getRequestURI())) {
                    return true;
                }
                return delegate.preHandle(request, response, handler);
            }
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/admin/**", "/api/shipping/**")
                .order(100);
    }
}
