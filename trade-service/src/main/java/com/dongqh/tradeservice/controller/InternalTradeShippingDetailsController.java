package com.dongqh.tradeservice.controller;

import com.dongqh.hmapi.dto.TradeOrderShippingDetailsDTO;
import com.dongqh.hmapi.dto.UpdateTradeOrderShippingInfoRequest;
import com.dongqh.tradeservice.service.InternalTradeShippingDetailsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
public class InternalTradeShippingDetailsController {
    private final InternalTradeShippingDetailsService shippingDetails;

    public InternalTradeShippingDetailsController(InternalTradeShippingDetailsService shippingDetails) {
        this.shippingDetails = shippingDetails;
    }

    @GetMapping("/{orderId}/shipping-details")
    public TradeOrderShippingDetailsDTO get(@PathVariable long orderId) {
        return shippingDetails.getPaidOrder(orderId);
    }

    @PutMapping("/{orderId}/shipping-info")
    public void updateShippingInfo(@PathVariable long orderId,
                                   @RequestBody UpdateTradeOrderShippingInfoRequest request) {
        shippingDetails.updateShippingInfo(orderId, request);
    }
}
