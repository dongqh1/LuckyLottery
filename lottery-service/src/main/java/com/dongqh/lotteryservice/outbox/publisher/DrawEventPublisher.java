package com.dongqh.lotteryservice.outbox.publisher;

import com.dongqh.lotteryservice.outbox.event.DrawEventEnvelope;

public interface DrawEventPublisher {
    void publish(DrawEventEnvelope event);
}
