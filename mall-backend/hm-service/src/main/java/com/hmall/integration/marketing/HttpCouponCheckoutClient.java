package com.hmall.integration.marketing;

import com.hmall.common.exception.BadRequestException;
import com.hmall.domain.dto.OrderDetailDTO;
import com.hmall.utils.AuthorizationContext;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class HttpCouponCheckoutClient implements CouponCheckoutClient {
    private final RestTemplate restTemplate;
    private final String checkoutUrl;

    @Autowired
    public HttpCouponCheckoutClient(
            RestTemplateBuilder builder,
            @Value("${hm.marketing.base-url}") String marketingBaseUrl) {
        this(builder.build(), stripTrailingSlash(marketingBaseUrl) + "/api/coupons/checkout");
    }

    HttpCouponCheckoutClient(RestTemplate restTemplate, String checkoutUrl) {
        this.restTemplate = restTemplate;
        this.checkoutUrl = stripTrailingSlash(checkoutUrl);
    }

    @Override
    public CouponReservationView reserve(Long couponId, Long orderId, List<OrderDetailDTO> details) {
        Map<String, Object> request = Map.of(
                "couponId", couponId,
                "orderNo", orderId.toString(),
                "items", details.stream()
                        .map(detail -> Map.<String, Object>of(
                                "itemId", detail.getItemId(),
                                "quantity", detail.getNum()))
                        .collect(Collectors.toList()));
        try {
            ResponseEntity<ApiResponse<CouponReservationView>> response = restTemplate.exchange(
                    checkoutUrl + "/reservations",
                    HttpMethod.POST,
                    new HttpEntity<>(request, authorizedHeaders()),
                    new org.springframework.core.ParameterizedTypeReference<ApiResponse<CouponReservationView>>() {
                    });
            ApiResponse<CouponReservationView> body = response.getBody();
            if (body == null || body.getData() == null) {
                throw new BadRequestException("优惠券预占失败");
            }
            return body.getData();
        } catch (RestClientException exception) {
            throw new BadRequestException("优惠券不可用或不适用于当前订单");
        }
    }

    @Override
    public void use(Long couponId, Long orderId) {
        transition(couponId, orderId, HttpMethod.PUT, "/used");
    }

    @Override
    public void release(Long couponId, Long orderId) {
        String url = UriComponentsBuilder.fromUriString(checkoutUrl + "/reservations/{couponId}")
                .queryParam("orderNo", orderId.toString())
                .buildAndExpand(couponId)
                .toUriString();
        try {
            restTemplate.exchange(url, HttpMethod.DELETE,
                    new HttpEntity<>(authorizedHeaders()), Void.class);
        } catch (RestClientException exception) {
            throw new BadRequestException("优惠券释放失败");
        }
    }

    private void transition(Long couponId, Long orderId, HttpMethod method, String suffix) {
        Map<String, Object> request = Map.of("orderNo", orderId.toString());
        try {
            restTemplate.exchange(checkoutUrl + "/reservations/" + couponId + suffix,
                    method, new HttpEntity<>(request, authorizedHeaders()), Void.class);
        } catch (RestClientException exception) {
            throw new BadRequestException("优惠券核销失败");
        }
    }

    private HttpHeaders authorizedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, AuthorizationContext.require());
        return headers;
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @Data
    private static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;
    }
}
