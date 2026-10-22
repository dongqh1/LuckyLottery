package com.dongqh.hmapi.client;

import com.dongqh.hmapi.dto.CreateShippingOrderRequest;
import com.dongqh.hmapi.dto.ShippingOrderDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient("logistics-service")
public interface LogisticsClient {

    @PostMapping("/internal/shipping/orders")
    ShippingOrderDTO createShippingOrder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody CreateShippingOrderRequest request
    );

    @GetMapping("/internal/shipping/orders")
    List<ShippingOrderDTO> getShippingOrdersByIds(@RequestParam("ids") Collection<Long> ids);
}
