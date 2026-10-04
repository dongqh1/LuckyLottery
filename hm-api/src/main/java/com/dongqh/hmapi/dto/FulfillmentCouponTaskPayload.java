package com.dongqh.hmapi.dto;

public record FulfillmentCouponTaskPayload(String couponTemplateCode, Integer quantity, Long couponTemplateId) {
    public FulfillmentCouponTaskPayload(String couponTemplateCode, int quantity) {
        this(couponTemplateCode, quantity, null);
    }

    public FulfillmentCouponTaskPayload {
        if (couponTemplateCode == null || couponTemplateCode.isBlank()) {
            throw new IllegalArgumentException("coupon fulfillment payload is invalid");
        }
        quantity = quantity == null ? 1 : quantity;
        if (quantity <= 0 || (couponTemplateId != null && couponTemplateId <= 0)) {
            throw new IllegalArgumentException("coupon fulfillment payload is invalid");
        }
    }
}
