package com.dongqh.luckyhub.messaging.lottery.port;

import com.dongqh.luckyhub.messaging.lottery.event.DrawEventEnvelope;

public interface DrawEventPublisher {

    void publish(DrawEventEnvelope event);
}
