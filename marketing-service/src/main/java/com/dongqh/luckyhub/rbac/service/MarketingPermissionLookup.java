package com.dongqh.luckyhub.rbac.service;

import com.dongqh.hmsecurity.permission.PermissionLookup;
import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Bridges the existing Marketing RBAC store and the temporary HMall role mapping. */
@Component
public class MarketingPermissionLookup implements PermissionLookup {
    private final UserPermissionService userPermissions;
    private final RolePermissionResolver rolePermissions;

    public MarketingPermissionLookup(UserPermissionService userPermissions,
                                     RolePermissionResolver rolePermissions) {
        this.userPermissions = userPermissions;
        this.rolePermissions = rolePermissions;
    }

    @Override
    public Set<String> findPermissionCodes(Long userId) {
        LoginPrincipal principal = LoginContext.get();
        if (principal == null || userId == null || !userId.equals(principal.userId())) {
            return Set.of();
        }
        if (principal.role() == null || principal.role().isBlank()) {
            Set<String> permissionCodes = userPermissions.findPermissionCodes(userId);
            return permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
        }
        return rolePermissions.resolve(principal.role());
    }
}
