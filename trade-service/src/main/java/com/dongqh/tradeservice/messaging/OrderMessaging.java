package com.dongqh.tradeservice.messaging;

public final class OrderMessaging {
    public static final String EVENT_EXCHANGE = "trade.events.topic";
    public static final String DEAD_LETTER_EXCHANGE = "trade.events.dlx";
    public static final String ORDER_CREATED_KEY = "trade.order.created";
    public static final String CART_QUEUE = "cart.trade.order-created.queue";
    public static final String CART_DEAD_LETTER_QUEUE = "cart.trade.order-created.dlq";

    private OrderMessaging() {
    }
}
