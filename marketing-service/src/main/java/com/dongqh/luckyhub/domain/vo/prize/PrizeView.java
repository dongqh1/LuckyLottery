package com.dongqh.luckyhub.domain.vo.prize;

import com.dongqh.luckyhub.enums.prize.PrizeLevel;
import com.dongqh.luckyhub.enums.prize.PrizeType;

import java.time.LocalDateTime;
import com.dongqh.luckyhub.enums.reward.RewardType;

public record PrizeView(
        Long id,
        String prizeName,
        PrizeType prizeType,
        PrizeLevel prizeLevel,
        String imageUrl,
        String description,
        Boolean stackable,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long rewardDefinitionId,
        RewardType rewardType
) {
    public PrizeView(Long id,String prizeName,PrizeType prizeType,PrizeLevel prizeLevel,
                     String imageUrl,String description,Boolean stackable,Integer status,
                     LocalDateTime createdAt,LocalDateTime updatedAt) {
        this(id,prizeName,prizeType,prizeLevel,imageUrl,description,stackable,status,
                createdAt,updatedAt,null,null);
    }
}
