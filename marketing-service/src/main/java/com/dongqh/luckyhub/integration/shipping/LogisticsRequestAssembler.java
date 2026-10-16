package com.dongqh.luckyhub.integration.shipping;

import com.dongqh.luckyhub.domain.model.fulfillment.FulfillmentClaim;
import com.dongqh.luckyhub.domain.model.fulfillment.LogisticsFulfillmentPayload;
import com.dongqh.luckyhub.domain.gateway.integration.LogisticsCreateRequest;

public interface LogisticsRequestAssembler {
    LogisticsCreateRequest assemble(FulfillmentClaim claim, LogisticsFulfillmentPayload payload);
}
