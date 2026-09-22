package com.hmall.common.config;

import org.apache.seata.core.context.RootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.junit.jupiter.api.Assertions.assertNull;

class SeataHttpClientAutoConfigurationTest {

    private final SeataHttpClientAutoConfiguration configuration =
            new SeataHttpClientAutoConfiguration();

    @AfterEach
    void clearXid() {
        RootContext.unbind();
    }

    @Test
    void restTemplatePropagatesBoundXid() {
        RestTemplate template = new RestTemplate();
        configuration.seataRestTemplateCustomizer(configuration.seataXidRequestInterceptor())
                .customize(template);
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(once(), requestTo("http://example.test/template"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(RootContext.KEY_XID, "xid-template"))
                .andRespond(withSuccess());

        RootContext.bind("xid-template");
        template.getForEntity("http://example.test/template", Void.class);

        server.verify();
    }

    @Test
    void restClientPropagatesBoundXid() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        configuration.seataRestClientCustomizer(configuration.seataXidRequestInterceptor())
                .customize(builder);
        RestClient client = builder.build();
        server.expect(once(), requestTo("http://example.test/client"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(RootContext.KEY_XID, "xid-client"))
                .andRespond(withSuccess());

        RootContext.bind("xid-client");
        client.get().uri("http://example.test/client").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void restClientOmitsXidOutsideGlobalTransaction() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        configuration.seataRestClientCustomizer(configuration.seataXidRequestInterceptor())
                .customize(builder);
        RestClient client = builder.build();
        server.expect(once(), requestTo("http://example.test/no-transaction"))
                .andExpect(request -> assertNull(request.getHeaders().getFirst(RootContext.KEY_XID)))
                .andRespond(withSuccess());

        client.get().uri("http://example.test/no-transaction").retrieve().toBodilessEntity();

        server.verify();
    }
}
