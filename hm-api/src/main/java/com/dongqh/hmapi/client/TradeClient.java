package com.dongqh.hmapi.client;

import com.dongqh.hmapi.dto.OrderPaymentDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("trade-service")
public interface TradeClient {

    @GetMapping("/orders/{orderId}/payment")
    OrderPaymentDTO queryOrderForPayment(
            @PathVariable("orderId") Long orderId,
            @RequestParam("userId") Long userId);

    @PutMapping("/orders/{orderId}/pay-success")
    void markOrderPaySuccess(
            @PathVariable("orderId") Long orderId,
            @RequestParam("userId") Long userId,
            @RequestParam("amount") Integer amount);
}
