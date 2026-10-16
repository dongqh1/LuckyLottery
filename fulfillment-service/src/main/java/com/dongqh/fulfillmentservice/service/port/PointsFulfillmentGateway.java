package com.dongqh.fulfillmentservice.service.port;

import com.dongqh.fulfillmentservice.domain.model.FulfillmentGatewayResult;
import com.dongqh.fulfillmentservice.domain.model.PointsFulfillmentPayload;

public interface PointsFulfillmentGateway {

    FulfillmentGatewayResult execute(String fulfillmentNo, long targetUserId, PointsFulfillmentPayload payload);

    FulfillmentGatewayResult query(String fulfillmentNo);
}
