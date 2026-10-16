package com.dongqh.luckyhub.domain.gateway.integration;
public interface CouponGateway { GatewayResult execute(CouponGrantRequest request); GatewayResult query(String fulfillmentNo); }
