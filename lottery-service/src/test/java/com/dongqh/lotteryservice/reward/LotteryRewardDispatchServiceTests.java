package com.dongqh.lotteryservice.reward;

import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.client.PointsClient;
import com.dongqh.hmapi.dto.CouponRewardIssueRequest;
import com.dongqh.hmapi.dto.CouponRewardTemplateDTO;
import com.dongqh.hmapi.dto.MembershipRewardGrantRequest;
import com.dongqh.hmapi.dto.MembershipRewardProductDTO;
import com.dongqh.hmapi.dto.PointsRewardCreditRequest;
import com.dongqh.lotteryservice.domain.po.draworder.DrawOrderRow;
import com.dongqh.lotteryservice.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lotteryservice.domain.po.prizecatalog.PrizeRow;
import com.dongqh.lotteryservice.domain.po.prizecatalog.RewardDefinitionRow;
import com.dongqh.lotteryservice.enums.draworder.DrawOrderStatus;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import com.dongqh.lotteryservice.mapper.draworder.DrawOrderMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lotteryservice.mapper.prizecatalog.PrizeCatalogMapper;
import com.dongqh.lotteryservice.mapper.prizecatalog.RewardDefinitionMapper;
import com.dongqh.lotteryservice.outbox.event.DrawEventEnvelope;
import com.dongqh.lotteryservice.outbox.event.DrawEventType;
import com.dongqh.lotteryservice.outbox.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.lotteryservice.outbox.mapper.LotteryRewardQuarantineMapper;
import com.dongqh.lotteryservice.outbox.mapper.MessageConsumeRecordMapper;
import com.dongqh.lotteryservice.service.drawchance.DrawChanceReservationService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LotteryRewardDispatchServiceTests {
    private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();

    @Test
    void issuesCurrentCouponDirectlyAndMakesBenefitAvailable() {
        Fixture fixture = fixture(RewardType.COUPON, 3L, true);
        when(fixture.coupons.getRewardTemplate(72L))
                .thenReturn(new CouponRewardTemplateDTO(72L, "CURRENT-CODE", 1));

        fixture.service.dispatch(fixture.envelope, fixture.payload);

        verify(fixture.coupons).issueReward(new CouponRewardIssueRequest(
                "LOTTERY-BENEFIT-41", 7L, 72L, "CURRENT-CODE", 3));
        verify(fixture.benefits).transitionStatus(41L, BenefitStatus.PENDING, BenefitStatus.AVAILABLE);
        verify(fixture.consumeRecords).insertIfAbsent(fixture.envelope.eventId().toString(), "test-lottery");
        verify(fixture.quarantines, never()).insertIfAbsent(anyString(), anyString(), any(), any(), any(),
                any(), any(), anyString());
    }

    @Test
    void creditsPointsDirectlyUsingStableIdempotencyKey() {
        Fixture fixture = fixture(RewardType.POINTS, 300L, true);

        fixture.service.dispatch(fixture.envelope, fixture.payload);

        verify(fixture.points).creditReward(new PointsRewardCreditRequest(
                "LOTTERY-BENEFIT-41", 7L, 300L, "抽奖奖励"));
        verify(fixture.benefits).transitionStatus(41L, BenefitStatus.PENDING, BenefitStatus.AVAILABLE);
    }

    @Test
    void grantsMembershipDirectlyWithoutMultiplyingDurationTwice() {
        Fixture fixture = fixture(RewardType.MEMBERSHIP, 3L, true);
        when(fixture.memberships.getRewardProduct(72L))
                .thenReturn(new MembershipRewardProductDTO(72L, "MEMBER-30D", "GOLD", 30, 1));

        fixture.service.dispatch(fixture.envelope, fixture.payload);

        verify(fixture.memberships).grantReward(new MembershipRewardGrantRequest(
                "LOTTERY-BENEFIT-41", 7L, 72L, "MEMBER-30D", 30, 3));
        verify(fixture.benefits).transitionStatus(41L, BenefitStatus.PENDING, BenefitStatus.AVAILABLE);
    }

    @Test
    void invalidCurrentRewardIsQuarantinedWithoutCreatingFulfillment() {
        Fixture fixture = fixture(RewardType.COUPON, 3L, false);

        fixture.service.dispatch(fixture.envelope, fixture.payload);

        verify(fixture.quarantines).insertIfAbsent(fixture.envelope.eventId().toString(), "request-11",
                11L, 31L, 41L, 51L, 61L, RewardQuarantineReason.CURRENT_REWARD_UNAVAILABLE.name());
        verify(fixture.coupons, never()).issueReward(any());
        verify(fixture.points, never()).creditReward(any());
        verify(fixture.memberships, never()).grantReward(any());
        verify(fixture.consumeRecords).insertIfAbsent(fixture.envelope.eventId().toString(), "test-lottery");
    }

    private Fixture fixture(RewardType rewardType, long quantity, boolean includeDefinition) {
        DrawOrderMapper orders = mock(DrawOrderMapper.class);
        DrawRecordMapper records = mock(DrawRecordMapper.class);
        UserBenefitMapper benefits = mock(UserBenefitMapper.class);
        PrizeCatalogMapper prizes = mock(PrizeCatalogMapper.class);
        RewardDefinitionMapper definitions = mock(RewardDefinitionMapper.class);
        ItemClient items = mock(ItemClient.class);
        CouponClient coupons = mock(CouponClient.class);
        MembershipClient memberships = mock(MembershipClient.class);
        PointsClient points = mock(PointsClient.class);
        LotteryRewardQuarantineMapper quarantines = mock(LotteryRewardQuarantineMapper.class);
        MessageConsumeRecordMapper consumeRecords = mock(MessageConsumeRecordMapper.class);
        DrawChanceReservationService drawChances = mock(DrawChanceReservationService.class);

        var payload = new PrizeFulfillmentRequestedEvent(41L, 31L, 51L);
        var envelope = DrawEventEnvelope.create(DrawEventType.PRIZE_FULFILLMENT_REQUESTED,
                "request-11", 7L, 9L, 11L, LocalDateTime.of(2026, 10, 20, 12, 0), payload, json);
        DrawOrderRow order = new DrawOrderRow();
        order.setId(11L);
        order.setRequestId("request-11");
        order.setUserId(7L);
        order.setActivityId(9L);
        order.setStatus(DrawOrderStatus.SUCCESS);
        DrawRecordRow record = new DrawRecordRow();
        record.setId(31L);
        record.setOrderId(11L);
        record.setRequestId("request-11");
        record.setUserId(7L);
        record.setActivityId(9L);
        record.setPrizeId(51L);
        UserBenefitRow benefit = new UserBenefitRow();
        benefit.setId(41L);
        benefit.setDrawRecordId(31L);
        benefit.setUserId(7L);
        benefit.setPrizeId(51L);
        benefit.setStatus(BenefitStatus.PENDING);
        benefit.setRewardDefinitionId(61L);

        when(orders.selectById(11L)).thenReturn(order);
        when(records.selectById(31L)).thenReturn(record);
        when(benefits.selectByIdForUpdate(41L)).thenReturn(benefit);
        when(consumeRecords.countByEventAndConsumer(envelope.eventId().toString(), "test-lottery")).thenReturn(0);

        PrizeRow prize = new PrizeRow();
        prize.setId(51L);
        prize.setPrizeType(switch (rewardType) {
            case COUPON -> PrizeType.COUPON;
            case POINTS -> PrizeType.POINTS;
            case MEMBERSHIP -> PrizeType.MEMBERSHIP;
            case PRODUCT -> PrizeType.PHYSICAL;
            case DRAW_CHANCE -> PrizeType.DRAW_CHANCE;
        });
        prize.setRewardDefinitionId(61L);
        prize.setStatus(1);
        when(prizes.selectById(51L)).thenReturn(prize);
        if (includeDefinition) {
            RewardDefinitionRow definition = new RewardDefinitionRow();
            definition.setId(61L);
            definition.setRewardType(rewardType);
            definition.setTargetId(rewardType == RewardType.POINTS || rewardType == RewardType.DRAW_CHANCE
                    ? null : 72L);
            definition.setQuantity(quantity);
            definition.setStatus(1);
            when(definitions.selectById(61L)).thenReturn(definition);
        }
        LotteryCurrentRewardResolver resolver = new LotteryCurrentRewardResolver(prizes, definitions,
                items, coupons, memberships);
        LotteryRewardDispatchService service = new LotteryRewardDispatchService(orders, records, benefits,
                new LotteryRewardIdentityVerifier(), resolver, quarantines, consumeRecords,
                coupons, points, memberships,
                drawChances, "test-lottery", Duration.ofDays(7));
        return new Fixture(service, envelope, payload, coupons, points, memberships,
                benefits, quarantines, consumeRecords);
    }

    private record Fixture(LotteryRewardDispatchService service, DrawEventEnvelope envelope,
                           PrizeFulfillmentRequestedEvent payload, CouponClient coupons,
                           PointsClient points, MembershipClient memberships,
                           UserBenefitMapper benefits, LotteryRewardQuarantineMapper quarantines,
                           MessageConsumeRecordMapper consumeRecords) { }
}
