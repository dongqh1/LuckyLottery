package com.dongqh.lottery.domain.model.prizecatalog;

import com.dongqh.lottery.enums.prizecatalog.PrizeType;
import com.dongqh.lottery.enums.prizecatalog.RewardType;

public final class RewardPrizeTypeMapping {
    private RewardPrizeTypeMapping() { }

    public static PrizeType toPrizeType(RewardType type) {
        if (type == null) throw new IllegalArgumentException("奖励类型不能为空");
        return switch (type) {
            case PRODUCT -> PrizeType.PHYSICAL;
            case COUPON -> PrizeType.COUPON;
            case POINTS -> PrizeType.POINTS;
            case MEMBERSHIP -> PrizeType.MEMBERSHIP;
            case DRAW_CHANCE -> PrizeType.DRAW_CHANCE;
        };
    }

    public static boolean matches(RewardType type, PrizeType prizeType) {
        return type != null && prizeType != null && toPrizeType(type) == prizeType;
    }
}
