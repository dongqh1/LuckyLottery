package com.dongqh.fulfillment.service.port;

import com.dongqh.fulfillment.domain.model.FulfillmentGatewayResult;
import com.dongqh.fulfillment.domain.model.shipping.LogisticsFulfillmentRequest;

public interface LogisticsFulfillmentGateway {

    FulfillmentGatewayResult execute(LogisticsFulfillmentRequest request);

    FulfillmentGatewayResult query(String fulfillmentNo);
}
