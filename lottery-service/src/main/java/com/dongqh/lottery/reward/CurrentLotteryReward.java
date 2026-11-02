package com.dongqh.lottery.reward;

import com.dongqh.hmapi.dto.CouponRewardTemplateDTO;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.hmapi.dto.MembershipRewardProductDTO;
import com.dongqh.lottery.domain.po.prizecatalog.PrizeRow;
import com.dongqh.lottery.domain.po.prizecatalog.RewardDefinitionRow;

public record CurrentLotteryReward(
        PrizeRow prize,
        RewardDefinitionRow definition,
        ItemDTO item,
        CouponRewardTemplateDTO couponTemplate,
        MembershipRewardProductDTO membershipProduct
) {
}
