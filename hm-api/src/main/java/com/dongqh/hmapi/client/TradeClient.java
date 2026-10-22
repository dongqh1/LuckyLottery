package com.dongqh.hmapi.client;

import com.dongqh.hmapi.dto.OrderPaymentDTO;
import com.dongqh.hmapi.dto.TradeOrderShippingDetailsDTO;
import com.dongqh.hmapi.dto.UpdateTradeOrderShippingInfoRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("trade-service")
public interface TradeClient {

    @GetMapping("/orders/{orderId}/payment")
    OrderPaymentDTO queryOrderForPayment(
            @PathVariable("orderId") Long orderId,
            @RequestParam("userId") Long userId);

    @GetMapping("/internal/orders/memberships/{orderId}/payment")
    OrderPaymentDTO queryMembershipOrderForPayment(
            @PathVariable("orderId") Long orderId,
            @RequestParam("userId") Long userId);

    @PutMapping("/orders/{orderId}/pay-success")
    void markOrderPaySuccess(
            @PathVariable("orderId") Long orderId,
            @RequestParam("userId") Long userId,
            @RequestParam("amount") Integer amount);

    @GetMapping("/internal/orders/{orderId}/shipping-details")
    TradeOrderShippingDetailsDTO getShippingDetails(@PathVariable("orderId") Long orderId);

    @PutMapping("/internal/orders/{orderId}/shipping-info")
    void updateShippingInfo(@PathVariable("orderId") Long orderId,
                            @RequestBody UpdateTradeOrderShippingInfoRequest request);
}
