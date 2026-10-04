package com.dongqh.fulfillmentservice.service.port;

import com.dongqh.fulfillmentservice.model.FulfillmentGatewayResult;
import com.dongqh.fulfillmentservice.shipping.model.LogisticsFulfillmentRequest;

public interface LogisticsFulfillmentGateway {

    FulfillmentGatewayResult execute(LogisticsFulfillmentRequest request);

    FulfillmentGatewayResult query(String fulfillmentNo);
}
