package com.dongqh.hmsecurity.permission;

import com.dongqh.hmsecurity.context.TrustedUserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Enforces permission annotations on paths registered as protected by the owning service.
 * Route registration and callback exemptions intentionally remain service-specific.
 */
public final class PermissionInterceptor implements HandlerInterceptor {
    private final PermissionLookup permissionLookup;

    public PermissionInterceptor(PermissionLookup permissionLookup) {
        this.permissionLookup = permissionLookup;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission requirement = findRequirement(handlerMethod);
        if (requirement == null || requirement.value().isBlank()) {
            throw new PermissionDeniedException("后台接口未配置访问权限");
        }

        Long userId = TrustedUserContext.getUserId();
        if (userId == null || userId <= 0) {
            throw new MissingTrustedUserException();
        }

        Set<String> permissionCodes;
        try {
            permissionCodes = permissionLookup.findPermissionCodes(userId);
        } catch (PermissionLookupUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PermissionLookupUnavailableException(exception);
        }
        if (permissionCodes == null || !permissionCodes.contains(requirement.value())) {
            throw new PermissionDeniedException("没有访问该接口的权限");
        }
        return true;
    }

    private RequirePermission findRequirement(HandlerMethod handlerMethod) {
        RequirePermission methodRequirement = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequirePermission.class);
        if (methodRequirement != null) {
            return methodRequirement;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequirePermission.class);
    }
}
