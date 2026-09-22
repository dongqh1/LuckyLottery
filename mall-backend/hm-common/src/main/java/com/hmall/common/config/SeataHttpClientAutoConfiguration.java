package com.hmall.common.config;

import org.apache.seata.core.context.RootContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@AutoConfiguration
@ConditionalOnClass({RootContext.class, RestTemplate.class, RestClient.class})
public class SeataHttpClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "seataXidRequestInterceptor")
    public ClientHttpRequestInterceptor seataXidRequestInterceptor() {
        return (request, body, execution) -> {
            String xid = RootContext.getXID();
            if (StringUtils.hasText(xid)) {
                request.getHeaders().set(RootContext.KEY_XID, xid);
            }
            return execution.execute(request, body);
        };
    }

    @Bean
    @ConditionalOnMissingBean(name = "seataRestTemplateCustomizer")
    public RestTemplateCustomizer seataRestTemplateCustomizer(
            @Qualifier("seataXidRequestInterceptor") ClientHttpRequestInterceptor seataXidRequestInterceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(seataXidRequestInterceptor);
    }

    @Bean
    @ConditionalOnMissingBean(name = "seataRestClientCustomizer")
    public RestClientCustomizer seataRestClientCustomizer(
            @Qualifier("seataXidRequestInterceptor") ClientHttpRequestInterceptor seataXidRequestInterceptor) {
        return builder -> builder.requestInterceptor(seataXidRequestInterceptor);
    }
}
