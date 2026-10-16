package com.dongqh.luckyhub.service.lottery;

import com.dongqh.luckyhub.messaging.lottery.event.DrawEventEnvelope;

public interface OutboxService {

    void append(DrawEventEnvelope event);
}
