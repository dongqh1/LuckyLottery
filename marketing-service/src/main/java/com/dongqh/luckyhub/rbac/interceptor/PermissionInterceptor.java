package com.dongqh.luckyhub.rbac.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Keeps Marketing's one callback exception while delegating permission checks to hm-security. */
@Component
public class PermissionInterceptor implements HandlerInterceptor {
    private static final String LOGISTICS_CALLBACK_PATH = "/api/shipping/callbacks/logistics";

    private final com.dongqh.hmsecurity.permission.PermissionInterceptor delegate;

    public PermissionInterceptor(com.dongqh.hmsecurity.permission.PermissionInterceptor delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.POST.matches(request.getMethod())
                && LOGISTICS_CALLBACK_PATH.equals(request.getRequestURI())) {
            return true;
        }
        return delegate.preHandle(request, response, handler);
    }
}
