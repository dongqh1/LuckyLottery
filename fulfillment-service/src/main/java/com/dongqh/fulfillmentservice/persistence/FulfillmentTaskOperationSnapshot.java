package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;

public record FulfillmentTaskOperationSnapshot(long id, FulfillmentStatus status) {
}
