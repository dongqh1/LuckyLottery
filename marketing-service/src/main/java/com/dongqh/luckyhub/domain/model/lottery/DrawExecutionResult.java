package com.dongqh.luckyhub.domain.model.lottery;

import com.dongqh.luckyhub.enums.lottery.DrawOrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record DrawExecutionResult(
        long orderId,
        String requestId,
        DrawOrderStatus status,
        LocalDateTime completedAt,
        List<DrawResultItem> items) {

    public DrawExecutionResult {
        items = List.copyOf(items);
    }
}
