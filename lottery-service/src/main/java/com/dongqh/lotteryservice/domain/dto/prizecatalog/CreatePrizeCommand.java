package com.dongqh.lotteryservice.domain.dto.prizecatalog;

import com.dongqh.lotteryservice.enums.prizecatalog.PrizeLevel;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

public record CreatePrizeCommand(
        @NotBlank(message = "奖品名称不能为空") @Size(max = 100, message = "奖品名称不能超过100个字符") String prizeName,
        @NotNull(message = "奖励类型不能为空") RewardType rewardType,
        @NotNull(message = "奖品等级不能为空") PrizeLevel prizeLevel,
        @Size(max = 500, message = "奖品图片地址不能超过500个字符") String imageUrl,
        @Size(max = 500, message = "奖品说明不能超过500个字符") String description,
        @NotNull(message = "是否可叠加不能为空") Boolean stackable,
        @Positive(message = "目标 ID 必须大于0") Long targetId,
        @NotNull(message = "发放数量不能为空") @Positive(message = "发放数量必须大于0") Long quantity,
        @Size(max = 64, message = "会员奖励编号不能超过64个字符") String membershipProductCode,
        @Size(max = 30, message = "会员等级不能超过30个字符") String membershipLevel,
        @Positive(message = "会员天数必须大于0") Integer durationDays
) {
    public CreatePrizeCommand(String prizeName, RewardType rewardType, PrizeLevel prizeLevel,
                              String imageUrl, String description, Boolean stackable,
                              Long targetId, Long quantity) {
        this(prizeName, rewardType, prizeLevel, imageUrl, description, stackable,
                targetId, quantity, null, null, null);
    }
}
