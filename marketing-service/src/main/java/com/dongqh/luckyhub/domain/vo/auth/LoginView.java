package com.dongqh.luckyhub.domain.vo.auth;

public record LoginView(
        Long userId,
        String username,
        String nickname,
        String token,
        String tokenType,
        Long expiresIn
) {
}
