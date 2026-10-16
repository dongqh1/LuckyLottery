package com.dongqh.luckyhub.domain.model.auth;

public record LoginPrincipal(
        Long userId,
        String username,
        String sessionId
) {
}
