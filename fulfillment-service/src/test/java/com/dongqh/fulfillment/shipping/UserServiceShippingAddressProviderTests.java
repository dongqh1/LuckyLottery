package com.dongqh.fulfillment.shipping;

import com.dongqh.fulfillment.integration.shipping.UserServiceShippingAddressProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceShippingAddressProviderTests {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardsCallerAuthorizationAndReturnsOnlyTheAddressOwnedByThatUser() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(MockRestRequestMatchers.requestTo("http://user-service/addresses/9"))
                .andExpect(MockRestRequestMatchers.header(HttpHeaders.AUTHORIZATION, "Bearer caller-token"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {"id":9,"userId":7,"province":"浙江省","city":"杭州市","town":"西湖区",
                         "mobile":"13800138000","street":"路 1 号","contact":"张三"}
                        """, org.springframework.http.MediaType.APPLICATION_JSON));
        setAuthorization("Bearer caller-token");

        var result = new UserServiceShippingAddressProvider(builder, "http://user-service")
                .findAddressForUser(7L, 9L);

        assertThat(result).isPresent().get().satisfies(address -> {
            assertThat(address.id()).isEqualTo(9L);
            assertThat(address.userId()).isEqualTo(7L);
            assertThat(address.toString()).doesNotContain("13800138000", "路 1 号", "张三");
        });
        server.verify();
    }

    @Test
    void doesNotReturnAnotherUsersAddressAndFailsClosedWithoutCallerAuthorization() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(MockRestRequestMatchers.requestTo("http://user-service/addresses/9"))
                .andExpect(MockRestRequestMatchers.header(HttpHeaders.AUTHORIZATION, "Bearer caller-token"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {"id":9,"userId":8,"province":"浙江省","city":"杭州市","town":"西湖区",
                         "mobile":"13800138000","street":"路 1 号","contact":"张三"}
                        """, org.springframework.http.MediaType.APPLICATION_JSON));
        setAuthorization("Bearer caller-token");

        var result = new UserServiceShippingAddressProvider(builder, "http://user-service")
                .findAddressForUser(7L, 9L);

        assertThat(result).isEmpty();
        server.verify();
        RequestContextHolder.resetRequestAttributes();
        assertThatThrownBy(() -> new UserServiceShippingAddressProvider(RestClient.builder(), "http://user-service")
                .findAddressForUser(7L, 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("地址查询缺少调用方认证信息");
    }

    private static void setAuthorization(String value) {
        HttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern", "/test");
        ((MockHttpServletRequest) request).addHeader(HttpHeaders.AUTHORIZATION, value);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
