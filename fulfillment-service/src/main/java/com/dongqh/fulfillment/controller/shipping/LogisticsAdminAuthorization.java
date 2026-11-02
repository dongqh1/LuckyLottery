package com.dongqh.fulfillment.controller.shipping;

import com.hmall.common.utils.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LogisticsAdminAuthorization {
    private final Set<Long> adminIds;

    public LogisticsAdminAuthorization(@Value("${luckyhub.logistics.admin-user-ids:1}") String configuredIds) {
        if (configuredIds == null || configuredIds.isBlank()) {
            throw new IllegalArgumentException("logistics admin user IDs must not be empty");
        }
        this.adminIds = Arrays.stream(configuredIds.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).map(Long::parseLong)
                .filter(id -> id > 0).collect(Collectors.toUnmodifiableSet());
        if (adminIds.isEmpty()) throw new IllegalArgumentException("logistics admin user IDs are invalid");
    }

    public long requireAdminId() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!adminIds.contains(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return userId;
    }
}
