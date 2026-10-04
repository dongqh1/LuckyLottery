package com.dongqh.fulfillmentservice.messaging;

public final class FulfillmentRabbitTopology {

    public static final String EXCHANGE = "fulfillment.direct";
    public static final String ROUTING_KEY = "fulfillment.task.ready";
    public static final String EXECUTE_QUEUE = "marketing.fulfillment.execute.queue";
    public static final String DEAD_LETTER_EXCHANGE = "fulfillment.dlx";
    public static final String DEAD_LETTER_QUEUE = "marketing.fulfillment.execute.dlq";

    private FulfillmentRabbitTopology() {
    }
}
