package com.dongqh.fulfillmentservice.shipping.integration;

import com.dongqh.fulfillmentservice.model.FulfillmentClaim;
import com.dongqh.fulfillmentservice.model.LogisticsFulfillmentPayload;
import com.dongqh.fulfillmentservice.shipping.model.LogisticsFulfillmentRequest;

public interface LogisticsRequestAssembler {

    LogisticsFulfillmentRequest assemble(FulfillmentClaim claim, LogisticsFulfillmentPayload payload);
}
