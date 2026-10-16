package com.dongqh.luckyhub.security.auth;

import com.dongqh.luckyhub.enums.auth.AuthErrorCode;
import com.dongqh.luckyhub.common.exception.BusinessException;

/** Extracts either a bare JWT or an RFC 6750-style Bearer JWT. */
public final class AuthorizationTokenExtractor {

    private static final String BEARER = "Bearer";

    private AuthorizationTokenExtractor() {
    }

    public static String extract(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw invalidToken();
        }

        String value = authorization.trim();
        if (value.length() >= BEARER.length()
                && value.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            if (value.length() == BEARER.length()) {
                throw invalidToken();
            }
            if (Character.isWhitespace(value.charAt(BEARER.length()))) {
                String token = value.substring(BEARER.length()).trim();
                if (token.isBlank()) {
                    throw invalidToken();
                }
                return token;
            }
        }

        return value;
    }

    private static BusinessException invalidToken() {
        return new BusinessException(AuthErrorCode.INVALID_TOKEN);
    }
}