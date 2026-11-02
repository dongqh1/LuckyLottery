package com.dongqh.fulfillment.service.port;

import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;

public interface FulfillmentStatusChangedOutbox {

    void append(FulfillmentStatusChangedEventV1 event);
}
