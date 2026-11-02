package com.dongqh.lottery.outbox.listener;

import com.dongqh.lottery.outbox.event.DrawEventEnvelope;
import com.dongqh.lottery.outbox.publisher.LotteryRabbitTopology;
import com.dongqh.lottery.outbox.service.LotteryEventConsumeService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "luckyhub.lottery.outbox", name = "enabled", havingValue = "true")
public class RabbitDrawEventListener {
    private final LotteryEventConsumeService events;

    public RabbitDrawEventListener(LotteryEventConsumeService events) {
        this.events = events;
    }

    @RabbitListener(queues = LotteryRabbitTopology.EVENT_QUEUE)
    public void onMessage(DrawEventEnvelope event) {
        events.consume(event);
    }
}
