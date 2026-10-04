package com.dongqh.hmapi.dto;

public record PointsRewardCreditRequest(String fulfillmentNo, long targetUserId,
                                        long points, String reason) {
    public PointsRewardCreditRequest {
        if (fulfillmentNo == null || fulfillmentNo.isBlank() || fulfillmentNo.length() > 64
                || targetUserId <= 0 || points <= 0 || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("points reward request is invalid");
        }
    }
}
