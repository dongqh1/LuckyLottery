package com.dongqh.fulfillmentservice.shipping.port;

import com.dongqh.fulfillmentservice.model.CreateFulfillmentTaskCommand;

public interface LogisticsTaskCreator {
    void create(CreateFulfillmentTaskCommand command);
}
