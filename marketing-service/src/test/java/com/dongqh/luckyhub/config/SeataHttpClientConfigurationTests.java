package com.dongqh.luckyhub.config;

import org.apache.seata.core.context.RootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SeataHttpClientConfigurationTests {

    private final SeataHttpClientConfiguration configuration =
            new SeataHttpClientConfiguration();

    @AfterEach
    void clearXid() {
        RootContext.unbind();
    }

    @Test
    void bootRestClientBuilderPropagatesBoundXid() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        configuration.seataRestClientCustomizer(configuration.seataXidRequestInterceptor())
                .customize(builder);
        RestClient client = builder.build();
        server.expect(once(), requestTo("http://example.test/marketing"))
                .andExpect(header(RootContext.KEY_XID, "xid-marketing"))
                .andRespond(withSuccess());

        RootContext.bind("xid-marketing");
        client.get().uri("http://example.test/marketing").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void bootRestClientBuilderOmitsXidOutsideGlobalTransaction() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        configuration.seataRestClientCustomizer(configuration.seataXidRequestInterceptor())
                .customize(builder);
        RestClient client = builder.build();
        server.expect(once(), requestTo("http://example.test/no-transaction"))
                .andExpect(request -> assertNull(
                        request.getHeaders().getFirst(RootContext.KEY_XID)))
                .andRespond(withSuccess());

        client.get().uri("http://example.test/no-transaction").retrieve().toBodilessEntity();

        server.verify();
    }
}
