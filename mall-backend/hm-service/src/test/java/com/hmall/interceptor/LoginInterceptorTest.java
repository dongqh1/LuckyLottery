package com.hmall.interceptor;

import com.hmall.common.exception.UnauthorizedException;
import com.hmall.utils.JwtTool;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LoginInterceptorTest {

    @Test
    void anonymousGetItemQueryIsPublic() throws Exception {
        JwtTool jwtTool = mock(JwtTool.class);
        LoginInterceptor interceptor = new LoginInterceptor(jwtTool);
        HttpServletRequest request = request("GET", "/items/123");

        assertThat(interceptor.preHandle(request, mock(HttpServletResponse.class), new Object())).isTrue();
        verifyNoInteractions(jwtTool);
    }

    @Test
    void anonymousItemWriteRequiresToken() {
        JwtTool jwtTool = mock(JwtTool.class);
        when(jwtTool.parseToken(null)).thenThrow(new UnauthorizedException("未登录"));
        LoginInterceptor interceptor = new LoginInterceptor(jwtTool);

        assertThatThrownBy(() -> interceptor.preHandle(
                request("POST", "/items"), mock(HttpServletResponse.class), new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void itemPathsAreNotExcludedFromAuthenticationInterceptor() throws Exception {
        ClassPathResource application = new ClassPathResource("application.yaml");
        String yaml = new String(application.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(yaml).doesNotContain("- /items/**");
    }

    private HttpServletRequest request(String method, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }
}
