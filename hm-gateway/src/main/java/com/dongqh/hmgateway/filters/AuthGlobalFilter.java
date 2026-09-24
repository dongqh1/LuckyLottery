package com.dongqh.hmgateway.filters;


import com.dongqh.hmgateway.config.AuthProperties;
import com.dongqh.hmgateway.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String USER_INFO_HEADER = "user-info";
    private static final String BEARER_PREFIX = "Bearer";

    private final AuthProperties authProperties;

    private final JwtTool jwtTool;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 外部请求不能自行声明内部身份头。
        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(builder -> builder.headers(headers -> headers.remove(USER_INFO_HEADER)))
                .build();
        ServerHttpRequest request = sanitizedExchange.getRequest();
        //2. 判断是否需要登录拦截
        if(isExclude(request.getMethod(), request.getPath().toString())){
            return chain.filter(sanitizedExchange);
        }

        //3.获取token
        String token = tokenForVerification(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        //4.校验并解析token
        Long userId = null;
        try {
            userId = jwtTool.parseToken(token);
        } catch (Exception e) {
            //拦截 ， 设置相应状态码401
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        //5.传递用户信息
        String userInfo = userId.toString();
        ServerWebExchange swe = sanitizedExchange.mutate()
                .request(builder -> builder.headers(headers -> headers.set(USER_INFO_HEADER, userInfo)))
                .build();

        //方向
        return chain.filter(swe);
    }

    private String tokenForVerification(String authorization) {
        if (authorization == null) {
            return null;
        }

        String value = authorization.trim();
        if (value.length() > BEARER_PREFIX.length()
                && value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                && Character.isWhitespace(value.charAt(BEARER_PREFIX.length()))) {
            return value.substring(BEARER_PREFIX.length()).trim();
        }
        return value;
    }

    private boolean isExclude(HttpMethod method, String path) {
        for(String rule : authProperties.getExcludePaths()){
            String[] parts = rule.trim().split("\\s+", 2);
            if(parts.length == 2
                    && HttpMethod.valueOf(parts[0].toUpperCase(Locale.ROOT)).equals(method)
                    && pathMatcher.match(parts[1], path)){
                return true;
            }
        }

        return false;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
