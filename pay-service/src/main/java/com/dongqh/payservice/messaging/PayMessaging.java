package com.dongqh.payservice.messaging;

public final class PayMessaging {
    public static final String EVENT_EXCHANGE = "pay.direct";
    public static final String PAY_SUCCESS_KEY = "pay.success";
    public static final String DEAD_LETTER_EXCHANGE = "pay.dlx";

    private PayMessaging() {
    }
}
