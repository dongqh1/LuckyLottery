package com.dongqh.luckyhub.messaging.fulfillment.rabbit;

import com.dongqh.luckyhub.messaging.fulfillment.event.FulfillmentTaskReadyEvent;
import com.dongqh.luckyhub.worker.fulfillment.FulfillmentWorker;
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
