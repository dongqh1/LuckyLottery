package com.dongqh.luckyhub.domain.model.auth;

public record JwtPayload(Long userId,
                         String username,
                         String sessionId) {
}
