package com.dongqh.lottery.reward;

import com.dongqh.lottery.domain.po.draworder.DrawOrderRow;
import com.dongqh.lottery.enums.draworder.DrawOrderStatus;
import com.dongqh.lottery.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lottery.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lottery.enums.drawtransaction.BenefitStatus;
import com.dongqh.lottery.outbox.event.DrawEventEnvelope;
import com.dongqh.lottery.outbox.event.DrawEventType;
import com.dongqh.lottery.outbox.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;
import com.dongqh.lottery.enums.prizecatalog.RewardType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LotteryRewardIdentityVerifierTests {
    private final LotteryRewardIdentityVerifier verifier = new LotteryRewardIdentityVerifier();

    @Test
    void validEventReturnsOnlyStableLotteryIdentity() {
        var rows = rows();
        var result = verifier.validate(rows.envelope, rows.payload, rows.order, rows.record, rows.benefit);

        assertThat(result).isEqualTo(new ValidatedLotteryReward(41L, 31L, 11L, "request-11",
                7L, 9L, 51L, BenefitStatus.PENDING));
        assertThat(ValidatedLotteryReward.class.isRecord()).isTrue();
        assertThat(ValidatedLotteryReward.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("benefitId", "drawRecordId", "orderId", "requestId", "userId",
                        "activityId", "prizeId", "benefitStatus");
    }

    @Test
    void missingAnyRowIsIdentityNotFound() {
        var rows = rows();
        mismatch(RewardQuarantineReason.IDENTITY_NOT_FOUND, rows.envelope, rows.payload,
                null, rows.record, rows.benefit);
        mismatch(RewardQuarantineReason.IDENTITY_NOT_FOUND, rows.envelope, rows.payload,
                rows.order, null, rows.benefit);
        mismatch(RewardQuarantineReason.IDENTITY_NOT_FOUND, rows.envelope, rows.payload,
                rows.order, rows.record, null);
    }

    @Test
    void orderCorrelationAndSuccessStateAreRequired() {
        var rows = rows();
        rows.order.setStatus(DrawOrderStatus.PROCESSING);
        mismatch(RewardQuarantineReason.ORDER_IDENTITY_MISMATCH, rows);
        rows.order.setStatus(DrawOrderStatus.SUCCESS);
        rows.order.setUserId(8L);
        mismatch(RewardQuarantineReason.ORDER_IDENTITY_MISMATCH, rows);
        rows.order.setUserId(7L);
        rows.order.setRequestId("another-request");
        mismatch(RewardQuarantineReason.ORDER_IDENTITY_MISMATCH, rows);
    }

    @Test
    void drawRecordMustCorrelateWithOrderAndEvent() {
        var rows = rows();
        rows.record.setOrderId(12L);
        mismatch(RewardQuarantineReason.RECORD_IDENTITY_MISMATCH, rows);
        rows.record.setOrderId(11L);
        rows.record.setRequestId("another-request");
        mismatch(RewardQuarantineReason.RECORD_IDENTITY_MISMATCH, rows);
        rows.record.setRequestId("request-11");
        rows.record.setPrizeId(52L);
        mismatch(RewardQuarantineReason.RECORD_IDENTITY_MISMATCH, rows);
    }

    @Test
    void benefitMustCorrelateWithDrawRecord() {
        var rows = rows();
        rows.benefit.setDrawRecordId(32L);
        mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH, rows);
        rows.benefit.setDrawRecordId(31L);
        rows.benefit.setUserId(8L);
        mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH, rows);
        rows.benefit.setUserId(7L);
        rows.benefit.setPrizeId(52L);
        mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH, rows);
    }

    @Test
    void currentCatalogProjectionIsNotPartOfStoredDrawIdentity() {
        var rows = rows();
        rows.record.setPrizeType(PrizeType.PHYSICAL);
        rows.record.setRewardDefinitionId(101L);
        rows.record.setRewardType(RewardType.PRODUCT);
        rows.record.setRewardTargetId(201L);
        rows.record.setRewardQuantity(4L);
        rows.benefit.setPrizeType(PrizeType.COUPON);
        rows.benefit.setRewardDefinitionId(102L);
        rows.benefit.setRewardType(RewardType.COUPON);
        rows.benefit.setRewardTargetId(202L);
        rows.benefit.setRewardQuantity(5L);

        assertThat(verifier.validate(rows.envelope, rows.payload, rows.order, rows.record, rows.benefit)
                .prizeId()).isEqualTo(51L);
    }

    private void mismatch(RewardQuarantineReason expected, Rows rows) {
        mismatch(expected, rows.envelope, rows.payload, rows.order, rows.record, rows.benefit);
    }

    private void mismatch(RewardQuarantineReason expected, DrawEventEnvelope envelope,
                          PrizeFulfillmentRequestedEvent payload, DrawOrderRow order,
                          DrawRecordRow record, UserBenefitRow benefit) {
        assertThatThrownBy(() -> verifier.validate(envelope, payload, order, record, benefit))
                .isInstanceOfSatisfying(RewardIdentityMismatchException.class,
                        error -> assertThat(error.reason()).isEqualTo(expected));
    }

    private Rows rows() {
        var payload = new PrizeFulfillmentRequestedEvent(41L, 31L, 51L);
        var envelope = DrawEventEnvelope.create(DrawEventType.PRIZE_FULFILLMENT_REQUESTED,
                "request-11", 7L, 9L, 11L, LocalDateTime.of(2026, 9, 28, 12, 0),
                payload, JsonMapper.builder().build());
        var order = new DrawOrderRow();
        order.setId(11L);
        order.setRequestId("request-11");
        order.setUserId(7L);
        order.setActivityId(9L);
        order.setStatus(DrawOrderStatus.SUCCESS);
        var record = new DrawRecordRow();
        record.setId(31L);
        record.setOrderId(11L);
        record.setRequestId("request-11");
        record.setUserId(7L);
        record.setActivityId(9L);
        record.setPrizeId(51L);
        record.setPrizeType(PrizeType.COUPON);
        record.setRewardDefinitionId(61L);
        record.setRewardType(RewardType.COUPON);
        record.setRewardTargetId(71L);
        record.setRewardQuantity(2L);
        var benefit = new UserBenefitRow();
        benefit.setId(41L);
        benefit.setDrawRecordId(31L);
        benefit.setUserId(7L);
        benefit.setPrizeId(51L);
        benefit.setPrizeType(PrizeType.COUPON);
        benefit.setStatus(BenefitStatus.PENDING);
        benefit.setRewardDefinitionId(61L);
        benefit.setRewardType(RewardType.COUPON);
        benefit.setRewardTargetId(71L);
        benefit.setRewardQuantity(2L);
        return new Rows(envelope, payload, order, record, benefit);
    }

    private record Rows(DrawEventEnvelope envelope, PrizeFulfillmentRequestedEvent payload,
                        DrawOrderRow order, DrawRecordRow record, UserBenefitRow benefit) { }
}
