package com.dongqh.luckyhub.domain.vo.reward;

import com.dongqh.luckyhub.enums.reward.RewardType;

import java.time.LocalDateTime;

public record RewardDefinitionView(
        Long id,
        String rewardCode,
        String rewardName,
        RewardType rewardType,
        Long targetId,
        Long quantity,
        String configSnapshot,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
