package com.dongqh.lotteryservice.reward;

import com.dongqh.lotteryservice.domain.po.draworder.DrawOrderRow;
import com.dongqh.lotteryservice.enums.draworder.DrawOrderStatus;
import com.dongqh.lotteryservice.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.outbox.event.DrawEventEnvelope;
import com.dongqh.lotteryservice.outbox.event.DrawEventType;
import com.dongqh.lotteryservice.outbox.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LotteryRewardIdentityVerifierTests {
    private static final String FINGERPRINT = "a".repeat(64);
    private final LotteryRewardIdentityVerifier verifier = new LotteryRewardIdentityVerifier();

    @Test
    void validFrozenSnapshotReturnsImmutableLotteryContext() {
        var rows = rows();
        var result = verifier.validate(rows.envelope, rows.payload, rows.order, rows.record, rows.benefit);

        assertThat(result).isEqualTo(new ValidatedLotteryReward(41L, 31L, 11L, "request-11",
                7L, 9L, 51L, PrizeType.COUPON, BenefitStatus.PENDING,
                new com.dongqh.lotteryservice.domain.model.drawtransaction.RewardSnapshot(61L, "FROZEN-61",
                        RewardType.COUPON, 71L, 2L, "{\"target\":71}", FINGERPRINT)));
        assertThat(ValidatedLotteryReward.class.isRecord()).isTrue();
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
        rows.benefit.setPrizeType(PrizeType.POINTS);
        mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH, rows);
    }

    @Test
    void rewardDefinitionMustMatchBothFrozenRows() {
        var rows = rows();
        rows.benefit.setRewardDefinitionId(62L);
        mismatch(RewardQuarantineReason.REWARD_DEFINITION_MISMATCH, rows);
    }

    @Test
    void rewardTypeMustMatchBothFrozenRows() {
        var rows = rows();
        rows.record.setRewardType(RewardType.POINTS);
        mismatch(RewardQuarantineReason.REWARD_TYPE_MISMATCH, rows);
    }

    @Test
    void rewardFingerprintMustMatchBothFrozenRows() {
        var rows = rows();
        rows.benefit.setRewardFingerprint("b".repeat(64));
        mismatch(RewardQuarantineReason.REWARD_FINGERPRINT_MISMATCH, rows);
    }

    @Test
    void rewardTargetQuantityAndRawJsonMustMatch() {
        var rows = rows();
        rows.benefit.setRewardTargetId(72L);
        mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH, rows);
        rows.benefit.setRewardTargetId(71L);
        rows.benefit.setRewardQuantity(3L);
        mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH, rows);
        rows.benefit.setRewardQuantity(2L);
        rows.benefit.setRewardPayload("{\"target\": 71}");
        mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH, rows);
    }

    @Test
    void missingRewardPayloadOrQuantityIsTypedMismatch() {
        var rows = rows();
        rows.record.setRewardPayload(null);
        rows.benefit.setRewardPayload(null);
        mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH, rows);
        rows = rows();
        rows.record.setRewardQuantity(null);
        rows.benefit.setRewardQuantity(null);
        mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH, rows);
    }

    @Test
    void noRewardSnapshotIgnoresTargetQuantityAndPayloadButRejectsMetadata() {
        var rows = rows();
        rows.record.setRewardDefinitionId(null);
        rows.benefit.setRewardDefinitionId(null);
        rows.record.setRewardType(null);
        rows.benefit.setRewardType(null);
        rows.record.setRewardFingerprint(null);
        rows.benefit.setRewardFingerprint(null);
        var noReward = new PrizeFulfillmentRequestedEvent(41L, 31L, 51L, PrizeType.COUPON);

        assertThat(verifier.validate(rows.envelope, noReward, rows.order, rows.record, rows.benefit)
                .rewardSnapshot()).isNull();
        rows.benefit.setRewardTargetId(72L);
        rows.benefit.setRewardQuantity(3L);
        rows.benefit.setRewardPayload("{\"target\":72}");
        assertThat(verifier.validate(rows.envelope, noReward, rows.order, rows.record, rows.benefit)
                .rewardSnapshot()).isNull();
        rows.benefit.setRewardDefinitionId(61L);
        mismatch(RewardQuarantineReason.REWARD_DEFINITION_MISMATCH, rows.envelope, noReward,
                rows.order, rows.record, rows.benefit);
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
        var payload = new PrizeFulfillmentRequestedEvent(41L, 31L, 51L, PrizeType.COUPON,
                61L, RewardType.COUPON, FINGERPRINT);
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
        record.setRewardPayload("{\"target\":71}");
        record.setRewardFingerprint(FINGERPRINT);
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
        benefit.setRewardPayload("{\"target\":71}");
        benefit.setRewardFingerprint(FINGERPRINT);
        return new Rows(envelope, payload, order, record, benefit);
    }

    private record Rows(DrawEventEnvelope envelope, PrizeFulfillmentRequestedEvent payload,
                        DrawOrderRow order, DrawRecordRow record, UserBenefitRow benefit) { }
}
