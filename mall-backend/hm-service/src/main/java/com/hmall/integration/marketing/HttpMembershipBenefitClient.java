package com.hmall.integration.marketing;

import com.hmall.utils.AuthorizationContext;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@Component
public class HttpMembershipBenefitClient implements MembershipBenefitClient {
    private static final int FULL_PRICE_BASIS_POINTS = 10_000;

    private final RestTemplate restTemplate;
    private final String membershipUrl;

    @Autowired
    public HttpMembershipBenefitClient(
            RestTemplateBuilder builder,
            @Value("${hm.membership.base-url}") String membershipServiceBaseUrl) {
        this(builder.build(), stripTrailingSlash(membershipServiceBaseUrl) + "/api/memberships/me");
    }

    HttpMembershipBenefitClient(RestTemplate restTemplate, String membershipUrl) {
        this.restTemplate = restTemplate;
        this.membershipUrl = membershipUrl;
    }

    @Override
    public MembershipBenefitView getActiveBenefit() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, AuthorizationContext.require());
        MembershipResponse response = restTemplate.exchange(
                membershipUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                MembershipResponse.class).getBody();
        if (response == null || response.getCode() != 0) {
            throw new IllegalStateException("会员权益查询失败");
        }
        MembershipData data = response.getData();
        if (data == null || data.getExpiresAt() == null || !data.getExpiresAt().isAfter(LocalDateTime.now())) {
            return new MembershipBenefitView(null, FULL_PRICE_BASIS_POINTS, null);
        }
        Integer basisPoints = data.getDiscountBasisPoints();
        if (basisPoints == null || basisPoints <= 0 || basisPoints > FULL_PRICE_BASIS_POINTS) {
            throw new IllegalStateException("会员折扣参数无效");
        }
        return new MembershipBenefitView(data.getMembershipLevel(), basisPoints, data.getExpiresAt());
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("会员服务地址不能为空");
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @Data
    public static class MembershipResponse {
        private int code;
        private String message;
        private MembershipData data;
    }

    @Data
    public static class MembershipData {
        private String membershipLevel;
        private Integer discountBasisPoints;
        private LocalDateTime expiresAt;
    }
}
