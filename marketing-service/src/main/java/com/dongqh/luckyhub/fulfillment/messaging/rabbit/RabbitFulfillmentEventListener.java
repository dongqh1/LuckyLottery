package com.dongqh.luckyhub.fulfillment.messaging.rabbit;

import com.dongqh.luckyhub.fulfillment.messaging.event.FulfillmentTaskReadyEvent;
import com.dongqh.luckyhub.fulfillment.worker.FulfillmentWorker;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitFulfillmentEventListener {
    private final FulfillmentWorker worker;

    public RabbitFulfillmentEventListener(FulfillmentWorker worker) {
        this.worker = worker;
    }

    @RabbitListener(queues = FulfillmentRabbitTopology.EXECUTE_QUEUE)
    public void onMessage(FulfillmentTaskReadyEvent event) {
        worker.runOne(event.fulfillmentNo());
    }
}
