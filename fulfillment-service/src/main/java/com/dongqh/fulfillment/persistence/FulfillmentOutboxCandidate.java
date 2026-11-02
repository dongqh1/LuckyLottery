package com.dongqh.fulfillment.persistence;

public record FulfillmentOutboxCandidate(long id, String eventType, String payload, long retryCount) {
}
