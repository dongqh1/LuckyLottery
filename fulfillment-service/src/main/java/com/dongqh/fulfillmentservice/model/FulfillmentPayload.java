package com.dongqh.fulfillmentservice.model;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;

public sealed interface FulfillmentPayload permits CouponFulfillmentPayload,
        PointsFulfillmentPayload, MembershipFulfillmentPayload, LogisticsFulfillmentPayload {
    FulfillmentType fulfillmentType();
}
