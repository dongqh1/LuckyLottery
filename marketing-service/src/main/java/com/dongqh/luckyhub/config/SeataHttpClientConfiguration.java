package com.dongqh.luckyhub.config;

import org.apache.seata.core.context.RootContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class SeataHttpClientConfiguration {

    @Bean
    ClientHttpRequestInterceptor seataXidRequestInterceptor() {
        return (request, body, execution) -> {
            String xid = RootContext.getXID();
            if (StringUtils.hasText(xid)) {
                request.getHeaders().set(RootContext.KEY_XID, xid);
            }
            return execution.execute(request, body);
        };
    }

    @Bean
    RestTemplateCustomizer seataRestTemplateCustomizer(
            @Qualifier("seataXidRequestInterceptor") ClientHttpRequestInterceptor seataXidRequestInterceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(seataXidRequestInterceptor);
    }

    @Bean
    RestClientCustomizer seataRestClientCustomizer(
            @Qualifier("seataXidRequestInterceptor") ClientHttpRequestInterceptor seataXidRequestInterceptor) {
        return builder -> builder.requestInterceptor(seataXidRequestInterceptor);
    }
}
