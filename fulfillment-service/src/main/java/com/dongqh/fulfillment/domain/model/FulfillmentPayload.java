package com.dongqh.fulfillment.domain.model;

import com.dongqh.fulfillment.enums.FulfillmentType;

public sealed interface FulfillmentPayload permits LogisticsFulfillmentPayload {
    FulfillmentType fulfillmentType();
}
