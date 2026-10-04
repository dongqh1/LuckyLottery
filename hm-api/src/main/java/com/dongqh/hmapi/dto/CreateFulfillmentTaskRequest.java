package com.dongqh.hmapi.dto;

public record CreateFulfillmentTaskRequest(
        String fulfillmentNo,
        String sourceType,
        String sourceId,
        String fulfillmentType,
        Long targetUserId,
        String payloadJson,
        Integer maxAttempts
) {
    public CreateFulfillmentTaskRequest {
        fulfillmentNo = required(fulfillmentNo, "fulfillmentNo", 64);
        sourceType = required(sourceType, "sourceType", 32);
        sourceId = required(sourceId, "sourceId", 100);
        fulfillmentType = required(fulfillmentType, "fulfillmentType", 32);
        if (targetUserId == null || targetUserId <= 0) {
            throw new IllegalArgumentException("targetUserId must be positive");
        }
        payloadJson = required(payloadJson, "payloadJson", Integer.MAX_VALUE);
        if (maxAttempts != null && maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
