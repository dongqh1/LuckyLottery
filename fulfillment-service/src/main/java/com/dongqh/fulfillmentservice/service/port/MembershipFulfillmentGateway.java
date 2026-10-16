package com.dongqh.fulfillmentservice.service.port;

import com.dongqh.fulfillmentservice.domain.model.FulfillmentGatewayResult;
import com.dongqh.fulfillmentservice.domain.model.MembershipFulfillmentPayload;

public interface MembershipFulfillmentGateway {

    FulfillmentGatewayResult execute(
            String fulfillmentNo,
            long targetUserId,
            MembershipFulfillmentPayload payload
    );

    FulfillmentGatewayResult query(String fulfillmentNo);
}
