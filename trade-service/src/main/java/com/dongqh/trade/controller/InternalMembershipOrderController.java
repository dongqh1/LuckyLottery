package com.dongqh.trade.controller;

import com.dongqh.hmapi.dto.OrderPaymentDTO;
import com.dongqh.trade.service.TradeMembershipOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders/memberships")
public class InternalMembershipOrderController {
    private final TradeMembershipOrderService orders;

    public InternalMembershipOrderController(TradeMembershipOrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/{orderId}/payment")
    public OrderPaymentDTO paymentSnapshot(@PathVariable Long orderId, @RequestParam Long userId) {
        return orders.paymentSnapshot(orderId, userId);
    }
}
