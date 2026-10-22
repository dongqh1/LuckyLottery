package com.dongqh.fulfillmentservice.domain.model;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;

public sealed interface FulfillmentPayload permits LogisticsFulfillmentPayload {
    FulfillmentType fulfillmentType();
}
