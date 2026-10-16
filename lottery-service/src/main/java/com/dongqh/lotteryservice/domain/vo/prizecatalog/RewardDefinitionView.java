package com.dongqh.lotteryservice.domain.vo.prizecatalog;

import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;

import java.time.LocalDateTime;

public record RewardDefinitionView(Long id, String rewardCode, String rewardName, RewardType rewardType,
                                   Long targetId, Long quantity, String configSnapshot, Integer status,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
}
