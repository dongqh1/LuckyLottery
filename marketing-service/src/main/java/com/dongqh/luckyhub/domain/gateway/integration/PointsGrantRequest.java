package com.dongqh.luckyhub.domain.gateway.integration;
public record PointsGrantRequest(String fulfillmentNo, Long targetUserId, long points, String reason) implements GatewayRequest {
 public PointsGrantRequest { fulfillmentNo=GatewayValidation.required(fulfillmentNo,"fulfillmentNo"); targetUserId=GatewayValidation.positive(targetUserId,"targetUserId"); points=GatewayValidation.positive(points,"points"); reason=GatewayValidation.required(reason,"reason"); }
}
