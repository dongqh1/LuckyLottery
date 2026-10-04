package com.dongqh.userservice.controller;

import com.dongqh.userservice.mapper.UserMapper;
import com.dongqh.userservice.security.UserPermissionResolver;
import com.dongqh.userservice.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@RestController
@RequestMapping("/internal/permissions/users")
@RequiredArgsConstructor
public class InternalUserPermissionController {
    private static final String BEARER_PREFIX = "Bearer";

    private final UserMapper users;
    private final JwtTool jwtTool;
    private final UserPermissionResolver permissionResolver;

    @GetMapping("/{userId}")
    public Set<String> getPermissionCodes(
            @PathVariable("userId") Long userId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        Long authenticatedUserId = jwtTool.parseToken(extractToken(authorization));
        if (!userId.equals(authenticatedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (!Integer.valueOf(1).equals(users.selectStatusValueById(userId))) {
            return Set.of();
        }
        return permissionResolver.resolve(users.selectPermissionRoleById(userId));
    }

    private String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String token = authorization.trim();
        if (token.length() > BEARER_PREFIX.length()
                && token.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                && Character.isWhitespace(token.charAt(BEARER_PREFIX.length()))) {
            token = token.substring(BEARER_PREFIX.length()).trim();
        }
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return token;
    }
}
