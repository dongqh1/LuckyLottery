package com.dongqh.lotteryservice.security;

import com.hmall.common.utils.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LotteryAdminAuthorization {
    private final Set<Long> administratorIds;

    public LotteryAdminAuthorization(@Value("${luckyhub.lottery.admin-user-ids:1}") String configuredIds) {
        if (configuredIds == null || configuredIds.isBlank()) {
            throw new IllegalArgumentException("至少需要配置一个抽奖管理员用户 ID");
        }
        try {
            this.administratorIds = Arrays.stream(configuredIds.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .peek(id -> {
                        if (id <= 0) throw new IllegalArgumentException("管理员用户 ID 必须大于0");
                    })
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("抽奖管理员用户 ID 配置格式无效", exception);
        }
    }

    public long requireAdmin() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0 || !administratorIds.contains(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有抽奖管理权限");
        }
        return userId;
    }
}
