package com.dongqh.fulfillmentservice.service.port;

import com.dongqh.fulfillmentservice.model.FulfillmentGatewayResult;
import com.dongqh.fulfillmentservice.model.PointsFulfillmentPayload;

public interface PointsFulfillmentGateway {

    FulfillmentGatewayResult execute(String fulfillmentNo, long targetUserId, PointsFulfillmentPayload payload);

    FulfillmentGatewayResult query(String fulfillmentNo);
}
