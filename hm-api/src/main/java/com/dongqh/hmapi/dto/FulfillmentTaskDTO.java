package com.dongqh.hmapi.dto;

public record FulfillmentTaskDTO(
        Long id,
        String fulfillmentNo,
        String sourceType,
        String sourceId,
        String fulfillmentType,
        Long targetUserId,
        String requestFingerprint,
        String status
) {
}
