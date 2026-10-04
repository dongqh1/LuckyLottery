package com.dongqh.fulfillmentservice.service.port;

import com.dongqh.fulfillmentservice.model.CouponFulfillmentPayload;
import com.dongqh.fulfillmentservice.model.FulfillmentGatewayResult;

public interface CouponFulfillmentGateway {

    FulfillmentGatewayResult execute(String fulfillmentNo, long targetUserId, CouponFulfillmentPayload payload);

    FulfillmentGatewayResult query(String fulfillmentNo);
}
