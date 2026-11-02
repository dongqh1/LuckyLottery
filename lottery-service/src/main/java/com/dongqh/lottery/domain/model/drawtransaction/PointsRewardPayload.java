package com.dongqh.lottery.domain.model.drawtransaction;

public record PointsRewardPayload(long points, String reason) {
    public PointsRewardPayload {
        if (points <= 0 || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("积分奖励参数不合法");
        }
    }
}
