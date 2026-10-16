package com.dongqh.luckyhub.domain.model.fulfillment;
import com.dongqh.luckyhub.enums.fulfillment.FulfillmentType;
public record CouponFulfillmentPayload(String couponTemplateCode,int quantity) implements FulfillmentPayload {public CouponFulfillmentPayload{couponTemplateCode=PayloadValidation.required(couponTemplateCode,"couponTemplateCode");quantity=PayloadValidation.positive(quantity,"quantity");}public FulfillmentType fulfillmentType(){return FulfillmentType.COUPON;}}
