package com.dongqh.fulfillment.persistence;

import com.dongqh.fulfillment.enums.FulfillmentStatus;

public record FulfillmentTaskOperationSnapshot(long id, FulfillmentStatus status) {
}
