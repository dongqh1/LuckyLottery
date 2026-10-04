package com.dongqh.lotteryservice.outbox.listener;

import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import com.dongqh.hmapi.messaging.FulfillmentStatusRabbitTopology;
import com.dongqh.lotteryservice.reward.LotteryFulfillmentStatusProjectionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "luckyhub.lottery.outbox", name = "enabled", havingValue = "true")
public class RabbitFulfillmentStatusChangedListener {
    private final LotteryFulfillmentStatusProjectionService projection;

    public RabbitFulfillmentStatusChangedListener(LotteryFulfillmentStatusProjectionService projection) {
        this.projection = projection;
    }

    @RabbitListener(queues = FulfillmentStatusRabbitTopology.QUEUE)
    public void onMessage(FulfillmentStatusChangedEventV1 event) {
        projection.consume(event);
    }
}
