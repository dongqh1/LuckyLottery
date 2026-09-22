package com.dongqh.luckyhub.auth.model;

public record JwtPayload(Long userId,
                         String username,
                         String sessionId,
                         String role) {

    public JwtPayload(Long userId, String username, String sessionId) {
        this(userId, username, sessionId, null);
    }
}
