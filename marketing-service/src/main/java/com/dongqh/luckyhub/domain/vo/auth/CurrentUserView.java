package com.dongqh.luckyhub.domain.vo.auth;

public record CurrentUserView(Long userId,
                              String username,
                              String nickname,
                              Integer status
) {
}
