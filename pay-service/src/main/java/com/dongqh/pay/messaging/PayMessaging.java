package com.dongqh.pay.messaging;

public final class PayMessaging {
    public static final String EVENT_EXCHANGE = "pay.direct";
    public static final String PAY_SUCCESS_KEY = "pay.success";
    public static final String MEMBERSHIP_PAY_SUCCESS_KEY = "pay.membership.success";
    public static final String DEAD_LETTER_EXCHANGE = "pay.dlx";
    public static final String MEMBERSHIP_QUEUE = "membership.pay.success.queue";
    public static final String MEMBERSHIP_DLQ = "membership.pay.success.dlq";
    public static final String TRADE_MEMBERSHIP_QUEUE = "trade.membership.pay.success.queue";
    public static final String TRADE_MEMBERSHIP_DLQ = "trade.membership.pay.success.dlq";

    private PayMessaging() {
    }
}
