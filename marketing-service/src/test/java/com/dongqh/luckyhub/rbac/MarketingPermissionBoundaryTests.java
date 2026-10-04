package com.dongqh.luckyhub.rbac;

import com.dongqh.hmsecurity.context.TrustedUserContext;
import com.dongqh.hmsecurity.permission.MissingTrustedUserException;
import com.dongqh.hmsecurity.permission.PermissionDeniedException;
import com.dongqh.hmsecurity.permission.PermissionInterceptor;
import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.coupon.controller.CouponAdminController;
import com.dongqh.luckyhub.coupon.controller.CouponController;
import com.dongqh.luckyhub.rbac.service.MarketingPermissionLookup;
import com.dongqh.luckyhub.rbac.service.RolePermissionResolver;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketingPermissionBoundaryTests {

    @AfterEach
    void clearContexts() {
        LoginContext.clear();
        TrustedUserContext.clear();
    }

    @Test
    void loginContextBridgesTrustedUserIdAndClearsBothContexts() {
        LoginContext.set(new LoginPrincipal(42L, "legacy", "session-1"));

        assertThat(TrustedUserContext.getUserId()).isEqualTo(42L);

        LoginContext.clear();

        assertThat(TrustedUserContext.getUserId()).isNull();
    }

    @Test
    void legacyIdentityResolvesPermissionsFromMarketingRbacStore() {
        UserPermissionService userPermissions = mock(UserPermissionService.class);
        when(userPermissions.findPermissionCodes(42L)).thenReturn(Set.of("coupon:read"));
        MarketingPermissionLookup lookup = new MarketingPermissionLookup(
                userPermissions, new RolePermissionResolver());
        LoginContext.set(new LoginPrincipal(42L, "legacy", "session-1"));

        assertThat(lookup.findPermissionCodes(42L)).containsExactly("coupon:read");
        verify(userPermissions).findPermissionCodes(42L);
    }

    @Test
    void hmallRoleUsesCompatibilityMappingAndDoesNotReadMarketingRbacStore() {
        UserPermissionService userPermissions = mock(UserPermissionService.class);
        MarketingPermissionLookup lookup = new MarketingPermissionLookup(
                userPermissions, new RolePermissionResolver());
        LoginContext.set(new LoginPrincipal(42L, "42", "", "USER"));

        assertThat(lookup.findPermissionCodes(42L)).contains("coupon:read").doesNotContain("coupon:manage");
        verifyNoInteractions(userPermissions);
    }

    @Test
    void oldControllerAnnotationWorksWithSharedInterceptor() throws Exception {
        UserPermissionService userPermissions = mock(UserPermissionService.class);
        MarketingPermissionLookup lookup = new MarketingPermissionLookup(
                userPermissions, new RolePermissionResolver());
        PermissionInterceptor interceptor = new PermissionInterceptor(lookup);
        LoginContext.set(new LoginPrincipal(42L, "42", "", "USER"));

        assertThat(interceptor.preHandle(null, null, handler(new CouponController(null), "page"))).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(null, null,
                handler(new CouponAdminController(null), "createTemplate")))
                .isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(userPermissions);
    }

    @Test
    void missingOrMismatchedLegacyPrincipalFailsClosed() {
        UserPermissionService userPermissions = mock(UserPermissionService.class);
        MarketingPermissionLookup lookup = new MarketingPermissionLookup(
                userPermissions, new RolePermissionResolver());

        assertThat(lookup.findPermissionCodes(42L)).isEmpty();
        LoginContext.set(new LoginPrincipal(7L, "legacy", "session-1"));
        assertThat(lookup.findPermissionCodes(42L)).isEmpty();
        verifyNoInteractions(userPermissions);
    }

    @Test
    void sharedInterceptorRequiresTheBridgedTrustedContext() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor(userId -> Set.of("coupon:read"));

        assertThatThrownBy(() -> interceptor.preHandle(null, null,
                handler(new CouponController(null), "page")))
                .isInstanceOf(MissingTrustedUserException.class);
    }

    private HandlerMethod handler(Object controller, String methodName) throws Exception {
        Method method = java.util.Arrays.stream(controller.getClass().getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return new HandlerMethod(controller, method);
    }
}
