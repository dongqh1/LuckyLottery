package com.dongqh.coupon.messaging;

public final class CouponMessagingTopology {

    public static final String EXCHANGE = "pay.direct";
    public static final String ROUTING_KEY = "pay.success";
    public static final String QUEUE = "marketing.pay.coupon-use.queue";
    public static final String DEAD_LETTER_EXCHANGE = "pay.dlx";
    public static final String DEAD_LETTER_QUEUE = "marketing.pay.coupon-use.dlq";

    private CouponMessagingTopology() {
    }
}
