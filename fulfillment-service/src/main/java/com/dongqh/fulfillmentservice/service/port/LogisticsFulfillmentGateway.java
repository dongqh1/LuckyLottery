package com.dongqh.fulfillmentservice.service.port;

import com.dongqh.fulfillmentservice.domain.model.FulfillmentGatewayResult;
import com.dongqh.fulfillmentservice.domain.model.shipping.LogisticsFulfillmentRequest;

public interface LogisticsFulfillmentGateway {

    FulfillmentGatewayResult execute(LogisticsFulfillmentRequest request);

    FulfillmentGatewayResult query(String fulfillmentNo);
}
