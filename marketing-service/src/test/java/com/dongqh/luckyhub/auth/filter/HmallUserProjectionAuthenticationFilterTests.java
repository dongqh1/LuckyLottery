package com.dongqh.luckyhub.auth.filter;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.service.HmallUserProjectionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HmallUserProjectionAuthenticationFilterTests {

    @AfterEach
    void clearContext() {
        LoginContext.clear();
    }

    @Test
    void verifiedHmallIdentityIsProjectedBeforeControllerExecution() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        HmallUserProjectionService projectionService = mock(HmallUserProjectionService.class);
        AuthenticationFilter filter = new AuthenticationFilter(
                jwtService, projectionService, new ObjectMapper());
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn("token");
        when(jwtService.parse("token"))
                .thenReturn(new JwtPayload(2L, "2", null, "USER"));

        filter.doFilterInternal(request, response, chain);

        ArgumentCaptor<LoginPrincipal> principal = ArgumentCaptor.forClass(LoginPrincipal.class);
        verify(projectionService).ensureActiveHmallUser(principal.capture());
        assertThat(principal.getValue().userId()).isEqualTo(2L);
        assertThat(principal.getValue().role()).isEqualTo("USER");
        verify(chain).doFilter(request, response);
    }
}
