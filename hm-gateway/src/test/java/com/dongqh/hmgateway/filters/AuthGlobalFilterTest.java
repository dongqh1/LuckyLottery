package com.dongqh.hmgateway.filters;

import com.dongqh.hmgateway.config.AuthProperties;
import com.dongqh.hmgateway.utils.JwtTool;
import com.hmall.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthGlobalFilterTest {

    @Test
    void anonymousGetRouteIsMatchedByMethodAndDropsForgedUserInfo() {
        AuthProperties properties = properties("GET /items/**");
        JwtTool jwtTool = rejectingJwtTool();
        AuthGlobalFilter filter = new AuthGlobalFilter(properties, jwtTool);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/items/1").header("user-info", "999")
        );
        filter.filter(exchange, capture(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("user-info")).isNull();
    }

    @Test
    void anonymousWriteToPublicReadPathIsRejected() {
        AuthProperties properties = properties("GET /items/**");
        AuthGlobalFilter filter = new AuthGlobalFilter(properties, rejectingJwtTool());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/items")
        );

        filter.filter(exchange, capture(forwarded)).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authenticatedRouteOverwritesForgedUserInfoWithVerifiedIdentity() {
        AuthProperties properties = properties();
        JwtTool jwtTool = mock(JwtTool.class);
        when(jwtTool.parseToken("valid-token")).thenReturn(42L);
        AuthGlobalFilter filter = new AuthGlobalFilter(properties, jwtTool);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/orders/me")
                        .header(HttpHeaders.AUTHORIZATION, "valid-token")
                        .header("user-info", "999")
        );

        filter.filter(exchange, capture(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().get("user-info"))
                .containsExactly("42");
    }

    @Test
    void onlyExactPostLogisticsCallbackIsAnonymousAndSanitizesIdentity() {
        AuthGlobalFilter filter = new AuthGlobalFilter(
                properties("POST /api/shipping/callbacks/logistics"), rejectingJwtTool());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        MockServerWebExchange post = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/shipping/callbacks/logistics")
                        .header("user-info", "999"));
        filter.filter(post, capture(forwarded)).block();
        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("user-info")).isNull();

        forwarded.set(null);
        MockServerWebExchange get = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/shipping/callbacks/logistics"));
        filter.filter(get, capture(forwarded)).block();
        assertThat(forwarded.get()).isNull();
        assertThat(get.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void paymentCallbackIsNotAnonymousWithoutJwt() {
        AuthGlobalFilter filter = new AuthGlobalFilter(
                properties("POST /api/shipping/callbacks/logistics"), rejectingJwtTool());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/callbacks/payments"));

        filter.filter(exchange, capture(forwarded)).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private AuthProperties properties(String... excludePaths) {
        AuthProperties properties = new AuthProperties();
        properties.setExcludePaths(List.of(excludePaths));
        return properties;
    }

    private JwtTool rejectingJwtTool() {
        JwtTool jwtTool = mock(JwtTool.class);
        when(jwtTool.parseToken(any())).thenThrow(new UnauthorizedException("未登录"));
        return jwtTool;
    }

    private GatewayFilterChain capture(AtomicReference<ServerWebExchange> forwarded) {
        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }
}
