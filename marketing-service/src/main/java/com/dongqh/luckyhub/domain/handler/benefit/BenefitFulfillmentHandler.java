package com.dongqh.luckyhub.domain.handler.benefit;

import com.dongqh.luckyhub.domain.po.benefit.UserBenefit;
import com.dongqh.luckyhub.enums.benefit.BenefitStatus;
import com.dongqh.luckyhub.enums.prize.PrizeType;

public interface BenefitFulfillmentHandler {

    PrizeType prizeType();

    BenefitStatus fulfill(UserBenefit benefit, String eventId);
}
