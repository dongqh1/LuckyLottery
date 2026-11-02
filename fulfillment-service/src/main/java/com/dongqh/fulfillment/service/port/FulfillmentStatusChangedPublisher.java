package com.dongqh.fulfillment.service.port;

import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;

public interface FulfillmentStatusChangedPublisher {
    String EVENT_TYPE = "FULFILLMENT_STATUS_CHANGED_V1";

    void publish(FulfillmentStatusChangedEventV1 event);
}
