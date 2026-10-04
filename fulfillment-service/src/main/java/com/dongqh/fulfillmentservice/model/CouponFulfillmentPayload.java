package com.dongqh.fulfillmentservice.model;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;

public record CouponFulfillmentPayload(String couponTemplateCode, int quantity,
                                       Long couponTemplateId) implements FulfillmentPayload {

    public CouponFulfillmentPayload(String couponTemplateCode, int quantity) {
        this(couponTemplateCode, quantity, null);
    }

    public CouponFulfillmentPayload {
        couponTemplateCode = PayloadValidation.required(couponTemplateCode, "couponTemplateCode");
        quantity = PayloadValidation.positive(quantity, "quantity");
        if (couponTemplateId != null && couponTemplateId <= 0) {
            throw new IllegalArgumentException("couponTemplateId must be positive");
        }
    }

    @Override
    public FulfillmentType fulfillmentType() {
        return FulfillmentType.COUPON;
    }
}
