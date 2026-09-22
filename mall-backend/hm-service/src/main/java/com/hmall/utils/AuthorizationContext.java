package com.hmall.utils;

import com.hmall.common.exception.UnauthorizedException;

public final class AuthorizationContext {
    private static final ThreadLocal<String> AUTHORIZATION = new ThreadLocal<>();

    private AuthorizationContext() {
    }

    public static void set(String authorization) {
        AUTHORIZATION.set(authorization);
    }

    public static String require() {
        String authorization = AUTHORIZATION.get();
        if (authorization == null || authorization.isBlank()) {
            throw new UnauthorizedException("未登录");
        }
        return authorization;
    }

    public static void remove() {
        AUTHORIZATION.remove();
    }
}
