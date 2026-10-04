package com.dongqh.hmapi.dto;

public record FulfillmentMembershipTaskPayload(String membershipCode, int durationDays,
                                               Integer quantity, Long membershipProductId) {
    public FulfillmentMembershipTaskPayload(String membershipCode, int durationDays) {
        this(membershipCode, durationDays, 1, null);
    }

    public FulfillmentMembershipTaskPayload {
        if (membershipCode == null || membershipCode.isBlank() || durationDays <= 0) {
            throw new IllegalArgumentException("membership fulfillment payload is invalid");
        }
        quantity = quantity == null ? 1 : quantity;
        if (quantity <= 0 || (membershipProductId != null && membershipProductId <= 0)) {
            throw new IllegalArgumentException("membership fulfillment payload is invalid");
        }
    }
}
