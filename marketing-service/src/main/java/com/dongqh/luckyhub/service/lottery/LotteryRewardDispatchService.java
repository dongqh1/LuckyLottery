package com.dongqh.luckyhub.service.lottery;

import com.dongqh.luckyhub.messaging.lottery.event.DrawEventEnvelope;
import com.dongqh.luckyhub.messaging.lottery.event.PrizeFulfillmentRequestedEvent;

public interface LotteryRewardDispatchService {
    void dispatch(DrawEventEnvelope envelope, PrizeFulfillmentRequestedEvent payload);
}
