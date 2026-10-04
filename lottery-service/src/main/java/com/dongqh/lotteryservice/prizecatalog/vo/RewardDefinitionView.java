package com.dongqh.lotteryservice.prizecatalog.vo;

import com.dongqh.lotteryservice.prizecatalog.domain.RewardType;

import java.time.LocalDateTime;

public record RewardDefinitionView(Long id, String rewardCode, String rewardName, RewardType rewardType,
                                   Long targetId, Long quantity, String configSnapshot, Integer status,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
}
