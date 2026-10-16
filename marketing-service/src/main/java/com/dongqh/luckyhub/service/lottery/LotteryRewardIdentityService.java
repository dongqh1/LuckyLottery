package com.dongqh.luckyhub.service.lottery;

import com.dongqh.luckyhub.messaging.lottery.event.DrawEventEnvelope;
import com.dongqh.luckyhub.messaging.lottery.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.domain.model.lottery.ValidatedLotteryReward;

public interface LotteryRewardIdentityService {
    ValidatedLotteryReward validate(DrawEventEnvelope envelope,
                                    PrizeFulfillmentRequestedEvent payload);
}
