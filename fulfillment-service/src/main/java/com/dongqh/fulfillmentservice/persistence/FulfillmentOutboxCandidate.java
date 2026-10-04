package com.dongqh.fulfillmentservice.persistence;

public record FulfillmentOutboxCandidate(long id, String eventType, String payload, long retryCount) {
}
