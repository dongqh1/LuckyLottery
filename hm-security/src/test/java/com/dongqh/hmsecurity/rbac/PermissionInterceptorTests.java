package com.dongqh.hmsecurity.rbac;

import com.dongqh.hmsecurity.context.TrustedUserContext;
import com.dongqh.hmsecurity.permission.MissingTrustedUserException;
import com.dongqh.hmsecurity.permission.PermissionDeniedException;
import com.dongqh.hmsecurity.permission.PermissionInterceptor;
import com.dongqh.hmsecurity.permission.PermissionLookup;
import com.dongqh.hmsecurity.permission.PermissionLookupUnavailableException;
import com.dongqh.hmsecurity.permission.RequirePermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionInterceptorTests {

    @AfterEach
    void clearUserContext() {
        TrustedUserContext.clear();
    }

    @Test
    void allowsHandlerWhenPermissionLookupContainsRequiredCode() throws Exception {
        TrustedUserContext.setUserId(42L);
        PermissionInterceptor interceptor = new PermissionInterceptor(
                userId -> userId == 42L ? Set.of("coupon:read") : Set.of());

        boolean allowed = interceptor.preHandle(request(), response(), handler("coupon"));

        assertThat(allowed).isTrue();
    }

    @Test
    void deniesWhenNoTrustedUserIsPresent() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor(userId -> Set.of("coupon:read"));

        assertThatThrownBy(() -> interceptor.preHandle(request(), response(), handler("coupon")))
                .isInstanceOf(MissingTrustedUserException.class);
    }

    @Test
    void deniesWhenRequiredPermissionIsAbsent() throws Exception {
        TrustedUserContext.setUserId(42L);
        PermissionInterceptor interceptor = new PermissionInterceptor(userId -> Set.of());

        assertThatThrownBy(() -> interceptor.preHandle(request(), response(), handler("coupon")))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void failsUnavailableWhenPermissionLookupThrows() throws Exception {
        TrustedUserContext.setUserId(42L);
        PermissionInterceptor interceptor = new PermissionInterceptor(userId -> {
            throw new IllegalStateException("permission store unavailable");
        });

        assertThatThrownBy(() -> interceptor.preHandle(request(), response(), handler("coupon")))
                .isInstanceOf(PermissionLookupUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void deniesProtectedHandlerWhenPermissionAnnotationIsMissing() throws Exception {
        TrustedUserContext.setUserId(42L);
        PermissionInterceptor interceptor = new PermissionInterceptor(userId -> Set.of());

        assertThatThrownBy(() -> interceptor.preHandle(request(), response(), handler("unannotated")))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void blankPermissionAnnotationIsDenied() throws Exception {
        TrustedUserContext.setUserId(42L);
        PermissionInterceptor interceptor = new PermissionInterceptor(userId -> Set.of());

        assertThatThrownBy(() -> interceptor.preHandle(request(), response(), handler("blank")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("后台接口未配置访问权限");
    }

    @Test
    void classPermissionCanBeOverriddenByMethodPermission() throws Exception {
        TrustedUserContext.setUserId(42L);
        PermissionInterceptor interceptor = new PermissionInterceptor(
                userId -> Set.of("method:read"));
        Method method = ClassProtectedController.class.getMethod("overridden");
        HandlerMethod handler = new HandlerMethod(new ClassProtectedController(), method);

        assertThat(interceptor.preHandle(request(), response(), handler)).isTrue();
    }

    @Test
    void classPermissionAppliesToUnannotatedMethods() throws Exception {
        TrustedUserContext.setUserId(42L);
        PermissionInterceptor interceptor = new PermissionInterceptor(
                userId -> Set.of("class:read"));
        Method method = ClassProtectedController.class.getMethod("inherited");
        HandlerMethod handler = new HandlerMethod(new ClassProtectedController(), method);

        assertThat(interceptor.preHandle(request(), response(), handler)).isTrue();
    }

    @Test
    void nonControllerHandlersAreLeftForSpringRouting() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor(userId -> Set.of());

        assertThat(interceptor.preHandle(request(), response(), new Object())).isTrue();
    }

    @Test
    void mapsAuthorizationFailuresToFailClosedHttpStatuses() {
        assertThat(MissingTrustedUserException.class.getAnnotation(ResponseStatus.class).value())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(PermissionDeniedException.class.getAnnotation(ResponseStatus.class).value())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(PermissionLookupUnavailableException.class.getAnnotation(ResponseStatus.class).value())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private HandlerMethod handler(String methodName) throws Exception {
        Method method = ProtectedController.class.getMethod(methodName);
        return new HandlerMethod(new ProtectedController(), method);
    }

    static class ProtectedController {
        @RequirePermission("coupon:read")
        public void coupon() {
        }

        @RequirePermission("lottery:draw")
        public void lottery() {
        }

        @RequirePermission(" ")
        public void blank() {
        }

        public void unannotated() {
        }
    }

    @RequirePermission("class:read")
    static class ClassProtectedController {
        public void inherited() {
        }

        @RequirePermission("method:read")
        public void overridden() {
        }
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/coupons");
    }

    private MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }
}
