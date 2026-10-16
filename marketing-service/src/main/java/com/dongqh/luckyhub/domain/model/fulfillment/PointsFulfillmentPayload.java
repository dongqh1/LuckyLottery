package com.dongqh.luckyhub.domain.model.fulfillment;
import com.dongqh.luckyhub.enums.fulfillment.FulfillmentType;
public record PointsFulfillmentPayload(long points,String reason) implements FulfillmentPayload {public PointsFulfillmentPayload{points=PayloadValidation.positive(points,"points");reason=PayloadValidation.required(reason,"reason");}public FulfillmentType fulfillmentType(){return FulfillmentType.POINTS;}}
