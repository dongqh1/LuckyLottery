package com.dongqh.luckyhub.auth.model;

public record LoginPrincipal(
        Long userId,
        String username,
        String sessionId,
        String role
) {

    /**
     * Compatibility constructor for LuckyHub's original session identity.
     * Hmall-authenticated requests always use the four-argument constructor.
     */
    public LoginPrincipal(Long userId, String username, String sessionId) {
        this(userId, username, sessionId, null);
    }
}
