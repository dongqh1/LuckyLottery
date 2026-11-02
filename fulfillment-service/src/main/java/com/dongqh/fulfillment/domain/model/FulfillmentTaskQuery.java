package com.dongqh.fulfillment.domain.model;

import com.dongqh.fulfillment.enums.FulfillmentStatus;
import com.dongqh.fulfillment.enums.FulfillmentType;

public record FulfillmentTaskQuery(
        long page,
        long size,
        FulfillmentType fulfillmentType,
        FulfillmentStatus status,
        Long targetUserId,
        String sourceType,
        String sourceId
) {

    public FulfillmentTaskQuery {
        if (page < 1) {
            throw new IllegalArgumentException("page必须大于0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size必须为1到100");
        }
        if (targetUserId != null && targetUserId <= 0) {
            throw new IllegalArgumentException("targetUserId必须大于0");
        }
    }
}
