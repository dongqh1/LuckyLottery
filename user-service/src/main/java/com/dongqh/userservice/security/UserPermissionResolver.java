package com.dongqh.userservice.security;

import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Resolves the same USER and ADMIN service permissions issued by the existing Hmall role policy. */
@Component
public class UserPermissionResolver {
    private static final Set<String> USER_PERMISSIONS = Set.of(
            ServicePermissionCodes.LOTTERY_ACTIVITY_READ,
            ServicePermissionCodes.LOTTERY_DRAW,
            ServicePermissionCodes.LOTTERY_DRAW_READ,
            ServicePermissionCodes.LOTTERY_RECORD_READ,
            ServicePermissionCodes.BENEFIT_READ,
            ServicePermissionCodes.POINTS_READ,
            ServicePermissionCodes.POINTS_REDEEM,
            ServicePermissionCodes.COUPON_READ,
            ServicePermissionCodes.MEMBERSHIP_READ
    );

    private static final Set<String> ADMIN_PERMISSIONS = findAllServicePermissions();

    public Set<String> resolve(String role) {
        if (role == null) {
            return Set.of();
        }
        if ("ADMIN".equalsIgnoreCase(role.trim())) {
            return ADMIN_PERMISSIONS;
        }
        if ("USER".equalsIgnoreCase(role.trim())) {
            return USER_PERMISSIONS;
        }
        return Set.of();
    }

    private static Set<String> findAllServicePermissions() {
        LinkedHashSet<String> codes = Arrays.stream(ServicePermissionCodes.class.getFields())
                .filter(field -> field.getType() == String.class)
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .map(UserPermissionResolver::readCode)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        return Collections.unmodifiableSet(codes);
    }

    private static String readCode(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read service permission code " + field.getName(), exception);
        }
    }
}
