package com.dongqh.tradeservice.config;

import com.dongqh.tradeservice.utils.AuthorizationContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

public final class TradeAuthorizationHeaderInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AuthorizationContext.set(request.getHeader(HttpHeaders.AUTHORIZATION));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        AuthorizationContext.remove();
    }
}
