package com.dongqh.hmapi.dto;

import lombok.Data;

@Data
public class OrderPaymentDTO {
    private Long id;
    private Long userId;
    private Integer status;
    private Integer totalFee;
    private Long couponId;
    private OrderDomain orderDomain = OrderDomain.TRADE;
    private MembershipPurchaseSnapshotDTO membershipPurchase;
}
