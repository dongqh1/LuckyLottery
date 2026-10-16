package com.dongqh.luckyhub.lottery;

import com.dongqh.luckyhub.enums.benefit.BenefitStatus;
import com.dongqh.luckyhub.mapper.benefit.UserBenefitMapper;
import com.dongqh.luckyhub.service.benefit.BenefitFulfillmentService;
import com.dongqh.luckyhub.service.drawchance.DrawChanceService;
import com.dongqh.luckyhub.domain.dto.fulfillment.CreateFulfillmentTaskCommand;
import com.dongqh.luckyhub.enums.fulfillment.FulfillmentType;
import com.dongqh.luckyhub.service.fulfillment.FulfillmentTaskService;
import com.dongqh.luckyhub.config.lottery.MessagingProperties;
import com.dongqh.luckyhub.enums.lottery.RewardQuarantineReason;
import com.dongqh.luckyhub.domain.po.lottery.LotteryRewardQuarantine;
import com.dongqh.luckyhub.domain.po.lottery.MessageConsumeRecord;
import com.dongqh.luckyhub.mapper.lottery.LotteryRewardQuarantineMapper;
import com.dongqh.luckyhub.mapper.lottery.MessageConsumeRecordMapper;
import com.dongqh.luckyhub.messaging.lottery.event.*;
import com.dongqh.luckyhub.domain.model.lottery.ValidatedLotteryReward;
import com.dongqh.luckyhub.service.lottery.LotteryRewardIdentityService;
import com.dongqh.luckyhub.service.lottery.RewardIdentityMismatchException;
import com.dongqh.luckyhub.service.lottery.impl.LotteryRewardDispatchServiceImpl;
import com.dongqh.luckyhub.enums.prize.PrizeType;
import com.dongqh.luckyhub.enums.reward.RewardType;
import com.dongqh.luckyhub.domain.model.reward.RewardSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LotteryRewardDispatchTests {
    LotteryRewardIdentityService identities = mock(LotteryRewardIdentityService.class);
    BenefitFulfillmentService legacy = mock(BenefitFulfillmentService.class);
    FulfillmentTaskService tasks = mock(FulfillmentTaskService.class);
    DrawChanceService chances = mock(DrawChanceService.class);
    UserBenefitMapper benefits = mock(UserBenefitMapper.class);
    LotteryRewardQuarantineMapper quarantines = mock(LotteryRewardQuarantineMapper.class);
    MessageConsumeRecordMapper consumes = mock(MessageConsumeRecordMapper.class);
    ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
    LotteryRewardDispatchServiceImpl service = new LotteryRewardDispatchServiceImpl(identities, legacy, tasks, chances,
            benefits, quarantines, consumes, json, properties());

    @Test
    void couponSnapshotCreatesTypedTaskAndBindsDeterministicNumber() {
        RewardSnapshot snapshot = new RewardSnapshot(8L, "C", RewardType.COUPON, 9L, 2,
                "{\"templateId\":9,\"templateCode\":\"WELCOME\",\"quantity\":2}", "a".repeat(64));
        var validated = new ValidatedLotteryReward(1, 2, 3, "r", 4, 5, 6,
                PrizeType.COUPON, BenefitStatus.PENDING, snapshot);
        DrawEventEnvelope envelope = envelope();
        PrizeFulfillmentRequestedEvent payload = payload();
        when(identities.validate(envelope, payload)).thenReturn(validated);

        service.dispatch(envelope, payload);

        ArgumentCaptor<CreateFulfillmentTaskCommand> captor = ArgumentCaptor.forClass(CreateFulfillmentTaskCommand.class);
        verify(tasks).create(captor.capture());
        assertThat(captor.getValue().fulfillmentNo()).isEqualTo("LOTTERY-BENEFIT-1");
        assertThat(captor.getValue().fulfillmentType()).isEqualTo(FulfillmentType.COUPON);
        assertThat(captor.getValue().payload()).extracting("couponTemplateCode", "quantity")
                .containsExactly("WELCOME", 2);
        verify(benefits).bindFulfillmentNo(1, "LOTTERY-BENEFIT-1");
        verify(consumes).insert(any(MessageConsumeRecord.class));
    }

    @Test
    void forgedIdentityIsConsumedAndQuarantinedWithZeroRewardEffects() {
        DrawEventEnvelope envelope = envelope();
        PrizeFulfillmentRequestedEvent payload = payload();
        when(identities.validate(envelope, payload)).thenThrow(
                new RewardIdentityMismatchException(RewardQuarantineReason.ORDER_IDENTITY_MISMATCH));

        service.dispatch(envelope, payload);

        verify(quarantines).insert(argThat((LotteryRewardQuarantine row) -> row.getReasonCode().equals("ORDER_IDENTITY_MISMATCH")));
        verify(consumes).insert(any(MessageConsumeRecord.class));
        verifyNoInteractions(tasks, chances, legacy);
        verify(benefits, never()).transitionStatus(anyLong(), any(), any());
    }

    @Test
    void productSnapshotFreezesClaimDeadlineWithoutCreatingFulfillmentTask() {
        RewardSnapshot snapshot = new RewardSnapshot(8L, "P", RewardType.PRODUCT, 9L, 1,
                "{\"skuId\":9,\"skuCode\":\"SKU-9\",\"productName\":\"礼盒\",\"skuName\":\"默认\",\"quantity\":1}",
                "b".repeat(64));
        var validated = new ValidatedLotteryReward(1, 2, 3, "r", 4, 5, 6,
                PrizeType.PHYSICAL, BenefitStatus.PENDING, snapshot);
        DrawEventEnvelope envelope = envelope();
        PrizeFulfillmentRequestedEvent payload = payload();
        when(identities.validate(envelope, payload)).thenReturn(validated);
        when(benefits.markClaimPending(eq(1L), eq(BenefitStatus.PENDING), any())).thenReturn(1);
        LocalDateTime before = LocalDateTime.now().plusDays(7).minusSeconds(1);

        service.dispatch(envelope, payload);

        verify(benefits).markClaimPending(eq(1L), eq(BenefitStatus.PENDING), argThat(deadline ->
                deadline.isAfter(before) && deadline.isBefore(LocalDateTime.now().plusDays(7).plusSeconds(1))));
        verifyNoInteractions(tasks);
    }

    private DrawEventEnvelope envelope() {
        return new DrawEventEnvelope(UUID.randomUUID(), DrawEventType.PRIZE_FULFILLMENT_REQUESTED, 1,
                "r", 4L, 5L, 3L, LocalDateTime.now(), json.valueToTree(payload()));
    }
    private PrizeFulfillmentRequestedEvent payload() {
        return new PrizeFulfillmentRequestedEvent(1L, 2L, 6L, PrizeType.COUPON,
                8L, RewardType.COUPON, "a".repeat(64));
    }
    private MessagingProperties properties() {
        return new MessagingProperties(
                false, "test-consumer", Duration.ofSeconds(5), Duration.ofSeconds(1));
    }
}
