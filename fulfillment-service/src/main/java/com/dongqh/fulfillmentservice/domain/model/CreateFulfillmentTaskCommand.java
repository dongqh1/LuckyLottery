package com.dongqh.fulfillmentservice.domain.model;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;

public record CreateFulfillmentTaskCommand(
        String fulfillmentNo,
        String sourceType,
        String sourceId,
        FulfillmentType fulfillmentType,
        Long targetUserId,
        FulfillmentPayload payload,
        Integer maxAttempts
) {

    public CreateFulfillmentTaskCommand {
        fulfillmentNo = required(fulfillmentNo, "fulfillmentNo");
        sourceType = required(sourceType, "sourceType");
        sourceId = required(sourceId, "sourceId");
        if (fulfillmentType != FulfillmentType.LOGISTICS || payload == null
                || payload.fulfillmentType() != fulfillmentType) {
            throw new IllegalArgumentException("物流服务只接受实物发货任务");
        }
        if (targetUserId == null || targetUserId <= 0) {
            throw new IllegalArgumentException("targetUserId必须大于0");
        }
        maxAttempts = maxAttempts == null ? 5 : maxAttempts;
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException("maxAttempts必须为1到100");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value.trim();
    }
}
