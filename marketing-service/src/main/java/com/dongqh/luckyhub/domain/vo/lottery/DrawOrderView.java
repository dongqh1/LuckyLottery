package com.dongqh.luckyhub.domain.vo.lottery;

import com.dongqh.luckyhub.enums.lottery.DrawOrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DrawOrderView(
        long orderId, String requestId, long activityId, int drawCount, LocalDate drawDate,
        DrawOrderStatus status, String failReason, LocalDateTime completedAt,
        List<DrawResultView> results) {
    public DrawOrderView {
        results = List.copyOf(results);
    }
}
