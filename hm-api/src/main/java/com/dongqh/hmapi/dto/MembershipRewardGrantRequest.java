package com.dongqh.hmapi.dto;

public record MembershipRewardGrantRequest(String fulfillmentNo, long targetUserId,
                                           Long membershipProductId, String membershipCode,
                                           int durationDays, int quantity) {
    public MembershipRewardGrantRequest {
        if (fulfillmentNo == null || fulfillmentNo.isBlank() || fulfillmentNo.length() > 64
                || targetUserId <= 0 || (membershipProductId == null && (membershipCode == null
                || membershipCode.isBlank())) || (membershipProductId != null && membershipProductId <= 0)
                || durationDays <= 0 || quantity <= 0 || quantity > 100) {
            throw new IllegalArgumentException("membership reward request is invalid");
        }
    }
}
