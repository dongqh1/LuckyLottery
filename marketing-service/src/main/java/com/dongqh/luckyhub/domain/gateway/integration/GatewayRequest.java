package com.dongqh.luckyhub.domain.gateway.integration;
public sealed interface GatewayRequest permits CouponGrantRequest, PointsGrantRequest, MembershipGrantRequest, LogisticsCreateRequest {
    String fulfillmentNo();
    Long targetUserId();
}
