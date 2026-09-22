package com.dongqh.luckyhub.lottery.messaging;

import com.dongqh.luckyhub.lottery.messaging.event.DrawEventEnvelope;
import com.dongqh.luckyhub.lottery.messaging.event.DrawEventType;
import com.dongqh.luckyhub.lottery.messaging.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.lottery.messaging.port.DrawEventPublisher;
import com.dongqh.luckyhub.lottery.messaging.rabbit.LotteryRabbitTopology;
import com.dongqh.luckyhub.lottery.service.MessageConsumeService;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import com.dongqh.luckyhub.reward.enums.RewardType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "seata.enabled=false",
        "spring.task.scheduling.enabled=false",
        "luckyhub.messaging.enabled=true",
        "luckyhub.lottery.outbox-initial-delay=24h",
        "luckyhub.fulfillment.initial-delay=1h"
})
class RabbitLotteryMessagingIntegrationTests {

    @Autowired DrawEventPublisher publisher;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitAdmin rabbitAdmin;
    @MockitoSpyBean MessageConsumeService consumeService;

    private String requestId;
    private String eventId;
    private Long benefitId;
    private String fulfillmentNo;

    @BeforeEach
    void purgeLotteryQueue() {
        rabbitAdmin.purgeQueue(LotteryRabbitTopology.EVENT_QUEUE, false);
    }

    @AfterEach
    void cleanExactRows() {
        if (fulfillmentNo != null) {
            jdbc.update("DELETE FROM fulfillment_outbox WHERE fulfillment_no=?", fulfillmentNo);
            jdbc.update("DELETE FROM fulfillment_task WHERE fulfillment_no=?", fulfillmentNo);
        }
        if (eventId != null) {
            jdbc.update("DELETE FROM message_consume_record WHERE event_id=?", eventId);
        }
        if (requestId != null) {
            jdbc.update("DELETE b FROM user_benefit b JOIN lottery_draw_record r "
                    + "ON r.id=b.draw_record_id WHERE r.request_id=?", requestId);
            jdbc.update("DELETE FROM lottery_draw_record WHERE request_id=?", requestId);
            jdbc.update("DELETE FROM lottery_draw_order WHERE request_id=?", requestId);
        }
    }

    @Test
    void realRabbitMqRedeliveryCreatesOneConsumeRecordAndOneFulfillmentTask() {
        DrawEventEnvelope envelope = insertCouponRewardFixture();
        eventId = envelope.eventId().toString();

        publisher.publish(envelope);
        publisher.publish(envelope);

        verify(consumeService, timeout(10_000).times(2)).consume(argThat(
                event -> event.eventId().equals(envelope.eventId())));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM message_consume_record WHERE event_id=?",
                Long.class, eventId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM fulfillment_task WHERE fulfillment_no=?",
                Long.class, fulfillmentNo)).isOne();

        QueueInformation eventQueue = rabbitAdmin.getQueueInfo(LotteryRabbitTopology.EVENT_QUEUE);
        QueueInformation deadLetterQueue = rabbitAdmin.getQueueInfo(LotteryRabbitTopology.DEAD_LETTER_QUEUE);
        assertThat(eventQueue).isNotNull();
        assertThat(deadLetterQueue).isNotNull();
    }

    private DrawEventEnvelope insertCouponRewardFixture() {
        requestId = "rabbit-lottery-" + UUID.randomUUID();
        long userId = positiveId();
        long activityId = positiveId();
        long prizeId = positiveId();
        long definitionId = positiveId();
        String fingerprint = "c".repeat(64);
        String rewardPayload = "{\"templateId\":91,\"templateCode\":\"RABBIT-E2E\",\"quantity\":1}";

        jdbc.update("""
                INSERT INTO lottery_draw_order
                  (request_id,user_id,activity_id,draw_count,draw_date,status)
                VALUES (?,?,?,1,CURRENT_DATE,'SUCCESS')
                """, requestId, userId, activityId);
        long orderId = jdbc.queryForObject(
                "SELECT id FROM lottery_draw_order WHERE request_id=?", Long.class, requestId);

        jdbc.update("""
                INSERT INTO lottery_draw_record
                  (order_id,request_id,sequence_no,user_id,activity_id,result_type,prize_id,prize_name,
                   prize_type,reward_definition_id,reward_type,reward_target_id,reward_quantity,
                   reward_payload,reward_fingerprint,draw_time)
                VALUES (?,?,1,?,?,'WIN',?,'RabbitMQ联调券','COUPON',?,'COUPON',91,1,
                        CAST(? AS JSON),?,CURRENT_TIMESTAMP(3))
                """, orderId, requestId, userId, activityId, prizeId, definitionId,
                rewardPayload, fingerprint);
        long recordId = jdbc.queryForObject(
                "SELECT id FROM lottery_draw_record WHERE request_id=?", Long.class, requestId);

        jdbc.update("""
                INSERT INTO user_benefit
                  (draw_record_id,user_id,prize_id,prize_type,reward_definition_id,reward_type,
                   reward_target_id,reward_quantity,reward_payload,reward_fingerprint,quantity,status,obtained_at)
                VALUES (?,?,?,'COUPON',?,'COUPON',91,1,CAST(? AS JSON),?,1,'PENDING',CURRENT_TIMESTAMP(3))
                """, recordId, userId, prizeId, definitionId, rewardPayload, fingerprint);
        benefitId = jdbc.queryForObject(
                "SELECT id FROM user_benefit WHERE draw_record_id=?", Long.class, recordId);
        fulfillmentNo = "LOTTERY-BENEFIT-" + benefitId;

        PrizeFulfillmentRequestedEvent payload = new PrizeFulfillmentRequestedEvent(
                benefitId, recordId, prizeId, PrizeType.COUPON,
                definitionId, RewardType.COUPON, fingerprint);
        return DrawEventEnvelope.create(
                DrawEventType.PRIZE_FULFILLMENT_REQUESTED,
                requestId,
                userId,
                activityId,
                orderId,
                LocalDateTime.now(),
                payload,
                objectMapper);
    }

    private long positiveId() {
        long value = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return value == 0 ? 1 : value;
    }
}
