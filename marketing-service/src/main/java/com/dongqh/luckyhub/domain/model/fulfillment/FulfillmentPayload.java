package com.dongqh.luckyhub.domain.model.fulfillment;
import com.dongqh.luckyhub.enums.fulfillment.FulfillmentType;
public sealed interface FulfillmentPayload permits CouponFulfillmentPayload, PointsFulfillmentPayload, MembershipFulfillmentPayload, LogisticsFulfillmentPayload { FulfillmentType fulfillmentType(); }
