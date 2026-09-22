package com.hmall.integration.marketing;

import com.hmall.utils.AuthorizationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpMembershipBenefitClientTest {

    @AfterEach
    void clearAuthorization() {
        AuthorizationContext.remove();
    }

    @Test
    void expiredMembershipDoesNotReduceOrderAmountAndAuthorizationIsForwarded() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String url = "http://marketing/api/memberships/me";
        String authorization = "Bearer signed-hmall-jwt";
        AuthorizationContext.set(authorization);
        server.expect(once(), requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authorization))
                .andRespond(withSuccess("{\"code\":0,\"message\":\"success\",\"data\":{" +
                                "\"membershipLevel\":\"GOLD\",\"discountBasisPoints\":9500," +
                                "\"expiresAt\":\"" + LocalDateTime.now().minusDays(1) + "\"}}",
                        MediaType.APPLICATION_JSON));
        HttpMembershipBenefitClient client = new HttpMembershipBenefitClient(restTemplate, url);

        MembershipBenefitView result = client.getActiveBenefit();

        assertThat(result.getMembershipLevel()).isNull();
        assertThat(result.getDiscountBasisPoints()).isEqualTo(10_000);
        server.verify();
    }
}
