package com.dongqh.luckyhub.domain.filter.auth;

import com.dongqh.luckyhub.domain.context.auth.LoginContext;
import com.dongqh.luckyhub.enums.auth.AuthErrorCode;
import com.dongqh.luckyhub.domain.model.auth.JwtPayload;
import com.dongqh.luckyhub.domain.model.auth.LoginPrincipal;
import com.dongqh.luckyhub.security.auth.JwtService;
import com.dongqh.luckyhub.security.auth.AuthorizationTokenExtractor;
import com.dongqh.luckyhub.security.auth.SessionService;
import com.dongqh.luckyhub.service.auth.HmallUserProjectionService;
import com.dongqh.luckyhub.common.enums.ErrorCode;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.result.ErrorResponse;
import com.dongqh.luckyhub.common.web.RequestIdSupport;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class AuthenticationFilter extends OncePerRequestFilter {

    private static final String LOGISTICS_CALLBACK_PATH = "/api/shipping/callbacks/logistics";

    private static final String AUTHORIZATION = "Authorization";
    private final JwtService jwtService;
    private final HmallUserProjectionService userProjectionService;
    private final ObjectMapper objectMapper;

    public AuthenticationFilter(
            JwtService jwtService,
            HmallUserProjectionService userProjectionService,
            ObjectMapper objectMapper
    ) {
        this.jwtService = jwtService;
        this.userProjectionService = userProjectionService;
        this.objectMapper = objectMapper;
    }

    /** Compatibility constructor for tests that do not exercise Hmall user projection. */
    public AuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this(jwtService, ignored -> { }, objectMapper);
    }

    /** Compatibility constructor; Hmall authentication does not use this session service. */
    public AuthenticationFilter(
            JwtService jwtService,
            SessionService ignoredSessionService,
            ObjectMapper objectMapper
    ) {
        this(jwtService, ignored -> { }, objectMapper);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && LOGISTICS_CALLBACK_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            JwtPayload payload = jwtService.parse(token);

            LoginPrincipal principal = new LoginPrincipal(
                    payload.userId(),
                    payload.username(),
                    payload.sessionId()
            );

            userProjectionService.ensureActiveHmallUser(principal);
            LoginContext.set(principal);

            filterChain.doFilter(request, response);
        } catch (BusinessException exception) {
            writeError(
                    request,
                    response,
                    exception.getErrorCode(),
                    exception.getMessage()
            );
        } finally {
            LoginContext.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        return AuthorizationTokenExtractor.extract(
                request.getHeader(AUTHORIZATION)
        );
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode,
            String message
    ) throws IOException {
        ErrorResponse body = ErrorResponse.of(
                errorCode,
                message,
                RequestIdSupport.getRequestId(request),
                System.currentTimeMillis()
        );

        response.setStatus(errorCode.httpStatus().value());
        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );
        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        objectMapper.writeValue(
                response.getOutputStream(),
                body
        );
    }
}
