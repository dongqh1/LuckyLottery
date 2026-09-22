package com.dongqh.luckyhub.lottery.messaging.rabbit;

import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.service.MessageConsumeService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitDrawEventListener {

    private final MessageConsumeService consumeService;

    public RabbitDrawEventListener(MessageConsumeService consumeService) {
        this.consumeService = Objects.requireNonNull(
                consumeService, "consumeService must not be null");
    }

    @RabbitListener(queues = LotteryRabbitTopology.EVENT_QUEUE)
    public void onMessage(DrawEventEnvelope event) {
        consumeService.consume(event);
    }
}
