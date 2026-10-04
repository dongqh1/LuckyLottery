package com.dongqh.hmapi.messaging;

public final class FulfillmentStatusRabbitTopology {
    public static final String EXCHANGE = "fulfillment.direct";
    public static final String ROUTING_KEY = "fulfillment.status.changed";
    public static final String QUEUE = "lottery.fulfillment.status.queue";
    public static final String DEAD_LETTER_EXCHANGE = "fulfillment.dlx";
    public static final String DEAD_LETTER_ROUTING_KEY = "lottery.fulfillment.status.dead";
    public static final String DEAD_LETTER_QUEUE = "lottery.fulfillment.status.dlq";

    private FulfillmentStatusRabbitTopology() {
    }
}
