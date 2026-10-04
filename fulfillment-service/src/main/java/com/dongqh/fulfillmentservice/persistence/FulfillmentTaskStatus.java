package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;

public record FulfillmentTaskStatus(String fulfillmentNo, FulfillmentStatus status) {
}
