package com.dongqh.luckyhub.domain.model.fulfillment;
import com.dongqh.luckyhub.enums.fulfillment.FulfillmentType;
public record MembershipFulfillmentPayload(String membershipCode,int durationDays) implements FulfillmentPayload {public MembershipFulfillmentPayload{membershipCode=PayloadValidation.required(membershipCode,"membershipCode");durationDays=PayloadValidation.positive(durationDays,"durationDays");}public FulfillmentType fulfillmentType(){return FulfillmentType.MEMBERSHIP;}}
