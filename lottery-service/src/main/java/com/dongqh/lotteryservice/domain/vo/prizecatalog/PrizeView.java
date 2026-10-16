package com.dongqh.lotteryservice.domain.vo.prizecatalog;

import com.dongqh.lotteryservice.enums.prizecatalog.PrizeLevel;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import java.time.LocalDateTime;

public record PrizeView(Long id, String prizeName, PrizeType prizeType, PrizeLevel prizeLevel,
                        String imageUrl, String description, Boolean stackable, Integer status,
                        LocalDateTime createdAt, LocalDateTime updatedAt,
                        Long rewardDefinitionId, RewardType rewardType) {
    public PrizeView(Long id, String prizeName, PrizeType prizeType, PrizeLevel prizeLevel,
                     String imageUrl, String description, Boolean stackable, Integer status,
                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, prizeName, prizeType, prizeLevel, imageUrl, description, stackable, status,
                createdAt, updatedAt, null, null);
    }
}
