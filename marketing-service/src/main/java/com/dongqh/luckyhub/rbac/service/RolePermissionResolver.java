package com.dongqh.luckyhub.rbac.service;

import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Maps the trusted role claim in an Hmall JWT directly to LuckyHub permissions. */
@Component
public class RolePermissionResolver {

    private static final Set<String> USER_PERMISSIONS = Set.of(
            PermissionCodes.LOTTERY_ACTIVITY_READ,
            PermissionCodes.LOTTERY_DRAW,
            PermissionCodes.LOTTERY_DRAW_READ,
            PermissionCodes.LOTTERY_RECORD_READ,
            PermissionCodes.BENEFIT_READ,
            PermissionCodes.POINTS_READ,
            PermissionCodes.POINTS_REDEEM,
            PermissionCodes.COUPON_READ,
            PermissionCodes.MEMBERSHIP_READ
    );

    private static final Set<String> ALL_PERMISSIONS = findAllPermissionCodes();

    public Set<String> resolve(String role) {
        if ("ADMIN".equals(role)) {
            return ALL_PERMISSIONS;
        }
        if ("USER".equals(role)) {
            return USER_PERMISSIONS;
        }
        return Set.of();
    }

    private static Set<String> findAllPermissionCodes() {
        LinkedHashSet<String> codes = Arrays.stream(PermissionCodes.class.getFields())
                .filter(field -> field.getType() == String.class)
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .map(RolePermissionResolver::readCode)
                .collect(
                        LinkedHashSet::new,
                        LinkedHashSet::add,
                        LinkedHashSet::addAll
                );
        return Collections.unmodifiableSet(codes);
    }

    private static String readCode(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read permission code " + field.getName(), exception);
        }
    }
}