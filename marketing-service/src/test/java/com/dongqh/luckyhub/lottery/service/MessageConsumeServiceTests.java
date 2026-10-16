package com.dongqh.luckyhub.service.lottery;

import com.dongqh.luckyhub.service.drawchance.DrawChanceService;
import com.dongqh.luckyhub.config.lottery.MessagingProperties;
import com.dongqh.luckyhub.mapper.lottery.MessageConsumeRecordMapper;
import com.dongqh.luckyhub.messaging.lottery.event.DrawEventEnvelope;
import com.dongqh.luckyhub.messaging.lottery.event.DrawEventType;
import com.dongqh.luckyhub.messaging.lottery.event.DrawReleaseRequestedEvent;
import com.dongqh.luckyhub.domain.quota.lottery.DrawQuotaService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageConsumeServiceTests {

    @Test
    void releaseEventSettlesRewardedChanceBeforeOpeningRedisQuotaCapacity() {
        MessageConsumeRecordMapper records = mock(MessageConsumeRecordMapper.class);
        DrawQuotaService quota = mock(DrawQuotaService.class);
        DrawChanceService chances = mock(DrawChanceService.class);
        LotteryRewardDispatchService rewards = mock(LotteryRewardDispatchService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactions.getTransaction(any())).thenReturn(status);
        when(records.selectCount(any())).thenReturn(0L);
        ObjectMapper json = new ObjectMapper();
        MessagingProperties properties = new MessagingProperties(
                false, "test-consumer", Duration.ofSeconds(5), Duration.ofSeconds(30));
        MessageConsumeService service = new MessageConsumeService(
                records, quota, chances, rewards, json, properties, transactions);
        String requestId = UUID.randomUUID().toString();
        DrawEventEnvelope event = DrawEventEnvelope.create(
                DrawEventType.DRAW_RELEASE_REQUESTED, requestId, 1L, 2L, 3L,
                LocalDateTime.now(), new DrawReleaseRequestedEvent(
                        1, LocalDate.now(), "test failure"), json);

        service.consume(event);

        InOrder releaseOrder = inOrder(chances, quota);
        releaseOrder.verify(chances).release(requestId);
        releaseOrder.verify(quota).release(requestId);
        verify(transactions).commit(status);
    }
}
