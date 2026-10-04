package com.dongqh.hmapi.dto;

public record CouponRewardIssueRequest(String fulfillmentNo, long targetUserId,
                                       Long couponTemplateId, String couponTemplateCode,
                                       int quantity) {
    public CouponRewardIssueRequest {
        if (fulfillmentNo == null || fulfillmentNo.isBlank() || fulfillmentNo.length() > 64
                || targetUserId <= 0 || (couponTemplateId == null && (couponTemplateCode == null
                || couponTemplateCode.isBlank())) || (couponTemplateId != null && couponTemplateId <= 0)
                || quantity <= 0 || quantity > 100) {
            throw new IllegalArgumentException("coupon reward request is invalid");
        }
    }
}
