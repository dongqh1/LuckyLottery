package com.dongqh.lottery.domain.model.drawtransaction;

import com.dongqh.lottery.enums.draworder.DrawOrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record DrawExecutionResult(long orderId, String requestId, DrawOrderStatus status,
                                  LocalDateTime completedAt, List<DrawResultItem> items) {
    public DrawExecutionResult {
        items = List.copyOf(items);
    }
}
