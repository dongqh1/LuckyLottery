package com.dongqh.luckyhub.domain.model.auth;

public record AuthenticatedUser(
        Long userId,
        String username,
        String nickname
) {
}
