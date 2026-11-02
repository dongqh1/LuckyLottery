package com.dongqh.lottery.outbox.publisher;

import com.dongqh.lottery.outbox.event.DrawEventEnvelope;

public interface DrawEventPublisher {
    void publish(DrawEventEnvelope event);
}
