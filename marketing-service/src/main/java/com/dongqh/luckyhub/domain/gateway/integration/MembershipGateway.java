package com.dongqh.luckyhub.domain.gateway.integration;
public interface MembershipGateway { GatewayResult execute(MembershipGrantRequest request); GatewayResult query(String fulfillmentNo); }
