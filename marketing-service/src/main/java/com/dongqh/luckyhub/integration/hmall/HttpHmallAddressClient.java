package com.dongqh.luckyhub.integration.hmall;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.function.Supplier;

@Component
public class HttpHmallAddressClient implements HmallAddressClient {

    private final RestClient restClient;
    private final Supplier<String> authorization;

    @Autowired
    public HttpHmallAddressClient(
            RestClient.Builder builder,
            @Value("${luckyhub.integration.hmall.address-base-url:http://localhost:8083}") String baseUrl
    ) {
        this(builder, baseUrl, HttpHmallAddressClient::currentAuthorization);
    }

    HttpHmallAddressClient(RestClient.Builder builder, String baseUrl, Supplier<String> authorization) {
        this.restClient = builder.baseUrl(stripTrailingSlash(baseUrl)).build();
        this.authorization = authorization;
    }

    @Override
    public HmallAddressView getAddress(long addressId) {
        String token = authorization.get();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("商城地址查询缺少调用方认证信息");
        }
        try {
            return restClient.get()
                    .uri("/addresses/{addressId}", addressId)
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .retrieve()
                    .body(HmallAddressView.class);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest exception) {
            return null;
        }
    }

    private static String currentAuthorization() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getHeader(HttpHeaders.AUTHORIZATION);
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
