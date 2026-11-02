package com.dongqh.fulfillment.domain.model;

import com.dongqh.fulfillment.enums.AttemptOperation;
import com.dongqh.fulfillment.enums.FulfillmentType;

import java.time.LocalDateTime;
import java.util.Objects;

public record FulfillmentClaim(
        long taskId,
        String fulfillmentNo,
        FulfillmentType fulfillmentType,
        long targetUserId,
        String requestPayload,
        AttemptOperation operation,
        String leaseToken,
        LocalDateTime claimedAt,
        LocalDateTime leaseUntil
) {

    public FulfillmentClaim {
        if (taskId <= 0 || targetUserId <= 0) {
            throw new IllegalArgumentException("履约任务与用户 ID 必须为正数");
        }
        fulfillmentNo = required(fulfillmentNo, "fulfillmentNo");
        requestPayload = required(requestPayload, "requestPayload");
        leaseToken = required(leaseToken, "leaseToken");
        Objects.requireNonNull(fulfillmentType, "fulfillmentType must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(claimedAt, "claimedAt must not be null");
        Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
        if (!leaseUntil.isAfter(claimedAt)) {
            throw new IllegalArgumentException("leaseUntil 必须晚于 claimedAt");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.trim();
    }
}
