package com.dongqh.fulfillmentservice.model;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;

public record PointsFulfillmentPayload(long points, String reason) implements FulfillmentPayload {

    public PointsFulfillmentPayload {
        points = PayloadValidation.positive(points, "points");
        reason = PayloadValidation.required(reason, "reason");
    }

    @Override
    public FulfillmentType fulfillmentType() {
        return FulfillmentType.POINTS;
    }
}
