package com.dongqh.lottery.domain.vo.prizecatalog;

import com.dongqh.lottery.enums.prizecatalog.RewardType;

import java.time.LocalDateTime;

public record RewardDefinitionView(Long id, String rewardCode, String rewardName, RewardType rewardType,
                                   Long targetId, Long quantity, Integer status,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
}
