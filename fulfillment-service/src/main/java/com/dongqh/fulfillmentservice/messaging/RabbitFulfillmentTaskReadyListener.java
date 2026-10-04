package com.dongqh.fulfillmentservice.messaging;

import com.dongqh.fulfillmentservice.worker.FulfillmentWorker;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitFulfillmentTaskReadyListener {

    private final FulfillmentWorker worker;

    public RabbitFulfillmentTaskReadyListener(FulfillmentWorker worker) {
        this.worker = worker;
    }

    @RabbitListener(queues = FulfillmentRabbitTopology.EXECUTE_QUEUE)
    public void onMessage(FulfillmentTaskReadyEvent event) {
        worker.runOne(event.fulfillmentNo());
    }
}
