package com.dongqh.hmsecurity.permission;

import com.dongqh.hmsecurity.context.TrustedUserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;
import java.util.function.BiFunction;

public final class RequestPermissionLookup implements PermissionLookup {
    private final BiFunction<Long, String, Set<String>> permissionCodes;

    public RequestPermissionLookup(PermissionCodeClient client) {
        this.permissionCodes = client::findPermissionCodes;
    }

    @Override
    public Set<String> findPermissionCodes(Long userId) {
        Long trustedUserId = TrustedUserContext.getUserId();
        if (userId == null || userId <= 0 || !userId.equals(trustedUserId)) {
            return Set.of();
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw unavailable(new IllegalStateException("Current HTTP request is unavailable"));
        }
        HttpServletRequest request = attributes.getRequest();
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            throw unavailable(new IllegalStateException("Authorization header is unavailable"));
        }
        try {
            Set<String> resolved = permissionCodes.apply(userId, authorization);
            return resolved == null ? Set.of() : Set.copyOf(resolved);
        } catch (RuntimeException exception) {
            if (exception instanceof PermissionLookupUnavailableException unavailable) {
                throw unavailable;
            }
            throw unavailable(exception);
        }
    }

    private PermissionLookupUnavailableException unavailable(Throwable cause) {
        return new PermissionLookupUnavailableException(cause);
    }
}
