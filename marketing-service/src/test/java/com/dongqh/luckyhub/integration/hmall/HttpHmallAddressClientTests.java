package com.dongqh.luckyhub.domain.hmall.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpHmallAddressClientTests {

    @Test
    void forwardsCallerTokenAndReadsOwnedHmallAddress() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHmallAddressClient client = new HttpHmallAddressClient(
                builder, "http://hmall", () -> "Bearer signed-token");
        server.expect(once(), requestTo("http://hmall/addresses/61"))
                .andExpect(header("Authorization", "Bearer signed-token"))
                .andRespond(withSuccess("""
                        {"id":61,"province":"广东省","city":"广州市","town":"天河区",
                         "mobile":"13800138000","street":"体育西路1号","contact":"张三",
                         "isDefault":1,"notes":""}
                        """, MediaType.APPLICATION_JSON));

        HmallAddressView address = client.getAddress(61);

        assertThat(address.id()).isEqualTo(61L);
        assertThat(address.contact()).isEqualTo("张三");
        assertThat(address.town()).isEqualTo("天河区");
        server.verify();
    }
}
