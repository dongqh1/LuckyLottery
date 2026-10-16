package com.dongqh.luckyhub.domain.handler.benefit;

import com.dongqh.luckyhub.domain.po.benefit.UserBenefit;
import com.dongqh.luckyhub.enums.benefit.BenefitStatus;
import com.dongqh.luckyhub.enums.prize.PrizeType;
import org.springframework.stereotype.Component;

@Component
public class DrawChanceFulfillmentHandler implements BenefitFulfillmentHandler {
    @Override public PrizeType prizeType() { return PrizeType.DRAW_CHANCE; }
    @Override public BenefitStatus fulfill(UserBenefit benefit, String eventId) {
        return BenefitStatus.AVAILABLE;
    }
}
