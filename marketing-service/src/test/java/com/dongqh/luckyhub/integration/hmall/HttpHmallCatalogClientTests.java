package com.dongqh.luckyhub.integration.hmall;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpHmallCatalogClientTests {

    @Test
    void readsRawItemDtoFromHmallItemEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://mall-service:8080/items/1001"))
                .andRespond(withSuccess("""
                        {"id":1001,"name":"商城耳机","price":9900,"stock":8,
                         "image":"/imgs/1001.jpg","category":"数码","brand":"HMall",
                         "spec":"黑色","status":1}
                        """, MediaType.APPLICATION_JSON));
        HmallCatalogClient client = new HttpHmallCatalogClient(
                builder, "http://mall-service:8080");

        HmallItemView item = client.getItem(1001L);

        assertThat(item.id()).isEqualTo(1001L);
        assertThat(item.name()).isEqualTo("商城耳机");
        assertThat(item.availableForReward()).isTrue();
        server.verify();
    }
}
