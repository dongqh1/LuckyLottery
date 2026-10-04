package com.dongqh.userservice.controller;

import com.dongqh.userservice.mapper.UserMapper;
import com.dongqh.userservice.security.UserPermissionResolver;
import com.dongqh.userservice.utils.JwtTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalUserPermissionControllerTests {
    private final UserMapper users = mock(UserMapper.class);
    private final JwtTool jwtTool = mock(JwtTool.class);
    private InternalUserPermissionController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalUserPermissionController(users, jwtTool, new UserPermissionResolver());
    }

    @Test
    void returnsTheExistingUserRolePermissionsForTheMatchingActiveUser() {
        when(jwtTool.parseToken("valid-token")).thenReturn(42L);
        when(users.selectStatusValueById(42L)).thenReturn(1);
        when(users.selectPermissionRoleById(42L)).thenReturn("USER");

        Set<String> result = controller.getPermissionCodes(42L, "Bearer valid-token");

        assertThat(result).contains("points:read", "points:redeem", "coupon:read", "membership:read");
        assertThat(result).doesNotContain("points:adjust", "coupon:manage", "membership:manage");
    }

    @Test
    void returnsAllExtractedServicePermissionsForAnActiveAdministrator() {
        when(jwtTool.parseToken("admin-token")).thenReturn(7L);
        when(users.selectStatusValueById(7L)).thenReturn(1);
        when(users.selectPermissionRoleById(7L)).thenReturn("ADMIN");

        Set<String> result = controller.getPermissionCodes(7L, "Bearer admin-token");

        assertThat(result).contains("points:adjust", "coupon:manage", "fulfillment:operate");
    }

    @Test
    void rejectsPermissionLookupForAnotherUsersToken() {
        when(jwtTool.parseToken("valid-token")).thenReturn(42L);

        assertThatThrownBy(() -> controller.getPermissionCodes(43L, "Bearer valid-token"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(users);
    }

    @Test
    void returnsNoPermissionsForFrozenOrMissingUsers() {
        when(jwtTool.parseToken("valid-token")).thenReturn(42L);
        when(users.selectStatusValueById(42L)).thenReturn(2);
        when(users.selectStatusValueById(43L)).thenReturn(null);
        when(jwtTool.parseToken("missing-user-token")).thenReturn(43L);

        assertThat(controller.getPermissionCodes(42L, "Bearer valid-token")).isEmpty();
        assertThat(controller.getPermissionCodes(43L, "Bearer missing-user-token")).isEmpty();
        verify(users, org.mockito.Mockito.never()).selectPermissionRoleById(42L);
        verify(users, org.mockito.Mockito.never()).selectPermissionRoleById(43L);
    }

    @Test
    void returnsNoPermissionsForUnsupportedRoles() {
        when(jwtTool.parseToken("valid-token")).thenReturn(42L);
        when(users.selectStatusValueById(42L)).thenReturn(1);
        when(users.selectPermissionRoleById(42L)).thenReturn("UNKNOWN");

        assertThat(controller.getPermissionCodes(42L, "Bearer valid-token")).isEmpty();
    }

    @Test
    void rejectsMissingBearerAuthorization() {
        assertThatThrownBy(() -> controller.getPermissionCodes(42L, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(users, jwtTool);
    }
}
