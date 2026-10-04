package com.dongqh.lotteryservice.outbox.service;

import com.dongqh.lotteryservice.drawchance.service.DrawChanceReservationService;
import com.dongqh.lotteryservice.outbox.event.DrawConfirmedEvent;
import com.dongqh.lotteryservice.outbox.event.DrawEventEnvelope;
import com.dongqh.lotteryservice.outbox.event.DrawEventType;
import com.dongqh.lotteryservice.outbox.event.DrawReleaseRequestedEvent;
import com.dongqh.lotteryservice.outbox.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.lotteryservice.outbox.mapper.MessageConsumeRecordMapper;
import com.dongqh.lotteryservice.quota.DrawQuotaService;
import com.dongqh.lotteryservice.reward.LotteryRewardDispatchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class LotteryEventConsumeService {
    private final MessageConsumeRecordMapper consumeRecords;
    private final DrawQuotaService quota;
    private final DrawChanceReservationService drawChances;
    private final LotteryRewardDispatchService rewards;
    private final ObjectMapper json;
    private final String consumerName;

    public LotteryEventConsumeService(MessageConsumeRecordMapper consumeRecords,
            DrawQuotaService quota, DrawChanceReservationService drawChances,
            LotteryRewardDispatchService rewards, ObjectMapper json,
            @Value("${luckyhub.lottery.consumer-name:lottery-service}") String consumerName) {
        this.consumeRecords = consumeRecords;
        this.quota = quota;
        this.drawChances = drawChances;
        this.rewards = rewards;
        this.json = json;
        this.consumerName = consumerName;
    }

    @Transactional
    public void consume(DrawEventEnvelope event) {
        String eventId = event.eventId().toString();
        if (alreadyConsumed(eventId)) {
            return;
        }
        switch (event.eventType()) {
            case DRAW_CONFIRMED -> {
                event.payloadAs(DrawConfirmedEvent.class, json);
                quota.confirm(event.requestId());
                drawChances.confirmIfReserved(event.requestId());
                consumeRecords.insertIfAbsent(eventId, consumerName);
            }
            case DRAW_RELEASE_REQUESTED -> {
                event.payloadAs(DrawReleaseRequestedEvent.class, json);
                drawChances.releaseIfReserved(event.requestId());
                quota.release(event.requestId());
                consumeRecords.insertIfAbsent(eventId, consumerName);
            }
            case PRIZE_FULFILLMENT_REQUESTED -> rewards.dispatch(event,
                    event.payloadAs(PrizeFulfillmentRequestedEvent.class, json));
        }
    }

    private boolean alreadyConsumed(String eventId) {
        return consumeRecords.countByEventAndConsumer(eventId, consumerName) > 0;
    }
}
