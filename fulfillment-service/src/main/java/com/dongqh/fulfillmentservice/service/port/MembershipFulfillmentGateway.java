package com.dongqh.fulfillmentservice.service.port;

import com.dongqh.fulfillmentservice.model.FulfillmentGatewayResult;
import com.dongqh.fulfillmentservice.model.MembershipFulfillmentPayload;

public interface MembershipFulfillmentGateway {

    FulfillmentGatewayResult execute(
            String fulfillmentNo,
            long targetUserId,
            MembershipFulfillmentPayload payload
    );

    FulfillmentGatewayResult query(String fulfillmentNo);
}
