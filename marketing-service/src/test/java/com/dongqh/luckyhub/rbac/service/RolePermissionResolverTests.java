package com.dongqh.luckyhub.rbac.service;

import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionResolverTests {

    private final RolePermissionResolver resolver = new RolePermissionResolver();

    @Test
    void userReceivesOrdinaryLotteryPointsCouponMembershipAndBenefitPermissions() {
        assertThat(resolver.resolve("USER")).containsExactlyInAnyOrder(
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
        assertThat(resolver.resolve("USER")).doesNotContain(
                PermissionCodes.ACTIVITY_CREATE,
                PermissionCodes.POINTS_ADJUST,
                PermissionCodes.ROLE_PERMISSION_ASSIGN
        );
    }

    @Test
    void adminReceivesEveryDeclaredPermissionCode() {
        Set<String> allDeclaredCodes = Arrays.stream(PermissionCodes.class.getFields())
                .filter(field -> field.getType() == String.class)
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .map(RolePermissionResolverTests::read)
                .collect(Collectors.toSet());

        assertThat(resolver.resolve("ADMIN")).containsExactlyInAnyOrderElementsOf(allDeclaredCodes);
    }

    @Test
    void unknownRoleReceivesNoPermissions() {
        assertThat(resolver.resolve("OPERATOR")).isEmpty();
    }

    private static String read(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException exception) {
            throw new AssertionError(exception);
        }
    }
}
