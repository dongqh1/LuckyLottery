package com.dongqh.luckyhub.messaging.lottery.rabbit;

import com.dongqh.luckyhub.messaging.lottery.event.DrawEventType;

import java.util.Objects;

public final class LotteryRabbitTopology {

    public static final String EVENT_EXCHANGE = "lottery.events.topic";
    public static final String EVENT_QUEUE = "lottery.events.queue";
    public static final String DEAD_LETTER_EXCHANGE = "lottery.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "lottery.events.dlq";

    public static final String DRAW_CONFIRMED_KEY = "lottery.draw.confirmed";
    public static final String DRAW_RELEASE_REQUESTED_KEY = "lottery.draw.release-requested";
    public static final String PRIZE_FULFILLMENT_REQUESTED_KEY =
            "lottery.prize.fulfillment-requested";

    private LotteryRabbitTopology() {
    }

    public static String routingKey(DrawEventType eventType) {
        return switch (Objects.requireNonNull(eventType, "eventType must not be null")) {
            case DRAW_CONFIRMED -> DRAW_CONFIRMED_KEY;
            case DRAW_RELEASE_REQUESTED -> DRAW_RELEASE_REQUESTED_KEY;
            case PRIZE_FULFILLMENT_REQUESTED -> PRIZE_FULFILLMENT_REQUESTED_KEY;
        };
    }
}
