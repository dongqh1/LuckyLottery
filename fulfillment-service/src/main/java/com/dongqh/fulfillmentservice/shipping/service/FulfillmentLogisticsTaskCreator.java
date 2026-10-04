package com.dongqh.fulfillmentservice.shipping.service;

import com.dongqh.fulfillmentservice.model.CreateFulfillmentTaskCommand;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskCreationService;
import com.dongqh.fulfillmentservice.shipping.port.LogisticsTaskCreator;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentLogisticsTaskCreator implements LogisticsTaskCreator {

    private final FulfillmentTaskCreationService tasks;

    public FulfillmentLogisticsTaskCreator(FulfillmentTaskCreationService tasks) {
        this.tasks = tasks;
    }

    @Override
    public void create(CreateFulfillmentTaskCommand command) {
        tasks.create(command);
    }
}
