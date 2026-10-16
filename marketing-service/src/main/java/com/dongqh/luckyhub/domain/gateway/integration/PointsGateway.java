package com.dongqh.luckyhub.domain.gateway.integration;
public interface PointsGateway { GatewayResult execute(PointsGrantRequest request); GatewayResult query(String fulfillmentNo); }
