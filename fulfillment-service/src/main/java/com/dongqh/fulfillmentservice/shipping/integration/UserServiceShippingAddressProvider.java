package com.dongqh.fulfillmentservice.shipping.integration;

import com.dongqh.fulfillmentservice.shipping.model.ShippingAddressDetails;
import com.dongqh.fulfillmentservice.shipping.port.ShippingAddressProvider;
import com.dongqh.hmapi.dto.AddressDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Component
public class UserServiceShippingAddressProvider implements ShippingAddressProvider {

    private final RestClient client;

    public UserServiceShippingAddressProvider(
            RestClient.Builder builder,
            @Value("${luckyhub.fulfillment.user-service.base-url:http://localhost:8083}") String baseUrl
    ) {
        String normalizedBaseUrl = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = builder.baseUrl(normalizedBaseUrl).build();
    }

    @Override
    public Optional<ShippingAddressDetails> findAddressForUser(long userId, long addressId) {
        String authorization = currentAuthorization();
        if (authorization == null || authorization.isBlank()) {
            throw new IllegalStateException("地址查询缺少调用方认证信息");
        }
        AddressDTO address;
        try {
            address = client.get().uri("/addresses/{addressId}", addressId)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve().body(AddressDTO.class);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest exception) {
            return Optional.empty();
        }
        if (address == null || address.getId() == null || address.getUserId() == null
                || address.getId() != addressId || address.getUserId() != userId) {
            return Optional.empty();
        }
        return Optional.of(new ShippingAddressDetails(address.getId(), address.getUserId(), address.getProvince(),
                address.getCity(), address.getTown(), address.getMobile(), address.getStreet(), address.getContact()));
    }

    private static String currentAuthorization() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getHeader(HttpHeaders.AUTHORIZATION);
    }
}
