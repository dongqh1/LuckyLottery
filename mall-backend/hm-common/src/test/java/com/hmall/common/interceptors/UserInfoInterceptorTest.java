package com.hmall.common.interceptors;

import com.hmall.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserInfoInterceptorTest {

    private final UserInfoInterceptor interceptor = new UserInfoInterceptor();

    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    @Test
    void storesUserIdWhenGatewayHeaderIsPresent() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("user-info")).thenReturn("42");

        interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

        assertThat(UserContext.getUser()).isEqualTo(42L);
    }

    @Test
    void ignoresRequestWhenGatewayHeaderIsMissing() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("user-info")).thenReturn(null);

        boolean accepted = interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

        assertThat(accepted).isTrue();
        assertThat(UserContext.getUser()).isNull();
    }
}
