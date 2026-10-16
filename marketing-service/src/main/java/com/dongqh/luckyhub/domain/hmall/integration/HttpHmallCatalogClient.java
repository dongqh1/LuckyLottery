package com.dongqh.luckyhub.domain.hmall.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class HttpHmallCatalogClient implements HmallCatalogClient {

    private final RestClient restClient;

    @Autowired
    public HttpHmallCatalogClient(
            RestClient.Builder builder,
            @Value("${luckyhub.integration.hmall.catalog-base-url:http://localhost:8081}") String baseUrl
    ) {
        this.restClient = builder.baseUrl(stripTrailingSlash(baseUrl)).build();
    }

    @Override
    public HmallItemView getItem(long itemId) {
        try {
            return restClient.get()
                    .uri("/items/{itemId}", itemId)
                    .retrieve()
                    .body(HmallItemView.class);
        } catch (HttpClientErrorException.NotFound exception) {
            return null;
        }
    }

    private static String stripTrailingSlash(String value) {
        if (value != null && value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
