package com.dongqh.fulfillmentservice.domain.model;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;

public record MembershipFulfillmentPayload(String membershipCode, int durationDays,
                                           Integer quantity, Long membershipProductId) implements FulfillmentPayload {

    public MembershipFulfillmentPayload(String membershipCode, int durationDays) {
        this(membershipCode, durationDays, 1, null);
    }

    public MembershipFulfillmentPayload(String membershipCode, int durationDays, int quantity) {
        this(membershipCode, durationDays, quantity, null);
    }

    public MembershipFulfillmentPayload {
        membershipCode = PayloadValidation.required(membershipCode, "membershipCode");
        durationDays = PayloadValidation.positive(durationDays, "durationDays");
        quantity = quantity == null ? 1 : quantity;
        quantity = PayloadValidation.positive(quantity, "quantity");
        if (membershipProductId != null && membershipProductId <= 0) {
            throw new IllegalArgumentException("membershipProductId must be positive");
        }
    }

    @Override
    public FulfillmentType fulfillmentType() {
        return FulfillmentType.MEMBERSHIP;
    }
}
