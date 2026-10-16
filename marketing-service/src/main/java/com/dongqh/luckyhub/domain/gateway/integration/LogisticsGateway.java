package com.dongqh.luckyhub.domain.gateway.integration;
public interface LogisticsGateway { GatewayResult execute(LogisticsCreateRequest request); GatewayResult query(String fulfillmentNo); }
