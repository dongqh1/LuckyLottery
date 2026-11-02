package com.dongqh.lottery.domain.vo.prizecatalog;

import com.dongqh.lottery.enums.prizecatalog.PrizeLevel;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;
import com.dongqh.lottery.enums.prizecatalog.RewardType;

import java.time.LocalDateTime;

public record PrizeView(Long id, String prizeName, PrizeType prizeType, PrizeLevel prizeLevel,
                        String imageUrl, String description, Boolean stackable, Integer status,
                        LocalDateTime createdAt, LocalDateTime updatedAt,
                        RewardType rewardType, Long targetId, Long quantity) {
}
