package com.dongqh.hmapi.dto;

public record FulfillmentPointsTaskPayload(long points, String reason) {
    public FulfillmentPointsTaskPayload {
        if (points <= 0 || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("points fulfillment payload is invalid");
        }
    }
}
