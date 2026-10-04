package com.dongqh.lotteryservice.reward;

import com.dongqh.lotteryservice.draworder.entity.DrawOrderRow;
import com.dongqh.lotteryservice.draworder.enums.DrawOrderStatus;
import com.dongqh.lotteryservice.drawtransaction.entity.DrawRecordRow;
import com.dongqh.lotteryservice.drawtransaction.entity.UserBenefitRow;
import com.dongqh.lotteryservice.drawtransaction.model.RewardSnapshot;
import com.dongqh.lotteryservice.outbox.event.DrawEventEnvelope;
import com.dongqh.lotteryservice.outbox.event.DrawEventType;
import com.dongqh.lotteryservice.outbox.event.PrizeFulfillmentRequestedEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Checks already loaded Lottery rows without reading or changing persistence state. */
@Component
public final class LotteryRewardIdentityVerifier {
    public ValidatedLotteryReward validate(DrawEventEnvelope envelope,
            PrizeFulfillmentRequestedEvent payload, DrawOrderRow order,
            DrawRecordRow record, UserBenefitRow benefit) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(payload, "payload");
        if (order == null || record == null || benefit == null) {
            throw mismatch(RewardQuarantineReason.IDENTITY_NOT_FOUND);
        }
        if (envelope.eventType() != DrawEventType.PRIZE_FULFILLMENT_REQUESTED
                || !Objects.equals(envelope.requestId(), order.getRequestId())
                || !Objects.equals(envelope.orderId(), order.getId())
                || !Objects.equals(envelope.userId(), order.getUserId())
                || !Objects.equals(envelope.activityId(), order.getActivityId())
                || order.getStatus() != DrawOrderStatus.SUCCESS) {
            throw mismatch(RewardQuarantineReason.ORDER_IDENTITY_MISMATCH);
        }
        if (!Objects.equals(order.getId(), record.getOrderId())
                || !Objects.equals(order.getRequestId(), record.getRequestId())
                || !Objects.equals(order.getUserId(), record.getUserId())
                || !Objects.equals(order.getActivityId(), record.getActivityId())
                || !Objects.equals(payload.drawRecordId(), record.getId())
                || !Objects.equals(payload.prizeId(), record.getPrizeId())
                || payload.prizeType() != record.getPrizeType()) {
            throw mismatch(RewardQuarantineReason.RECORD_IDENTITY_MISMATCH);
        }
        if (!Objects.equals(payload.benefitId(), benefit.getId())
                || !Objects.equals(record.getId(), benefit.getDrawRecordId())
                || !Objects.equals(record.getUserId(), benefit.getUserId())
                || !Objects.equals(record.getPrizeId(), benefit.getPrizeId())
                || record.getPrizeType() != benefit.getPrizeType()) {
            throw mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH);
        }

        RewardSnapshot snapshot = validateReward(payload, record, benefit);
        return new ValidatedLotteryReward(benefit.getId(), record.getId(), order.getId(),
                order.getRequestId(), benefit.getUserId(), order.getActivityId(),
                benefit.getPrizeId(), benefit.getPrizeType(), benefit.getStatus(), snapshot);
    }

    private RewardSnapshot validateReward(PrizeFulfillmentRequestedEvent payload,
            DrawRecordRow record, UserBenefitRow benefit) {
        if (payload.rewardDefinitionId() == null) {
            if (record.getRewardDefinitionId() != null || benefit.getRewardDefinitionId() != null
                    || record.getRewardType() != null || benefit.getRewardType() != null
                    || record.getRewardFingerprint() != null || benefit.getRewardFingerprint() != null) {
                throw mismatch(RewardQuarantineReason.REWARD_DEFINITION_MISMATCH);
            }
            return null;
        }
        if (!Objects.equals(payload.rewardDefinitionId(), record.getRewardDefinitionId())
                || !Objects.equals(record.getRewardDefinitionId(), benefit.getRewardDefinitionId())) {
            throw mismatch(RewardQuarantineReason.REWARD_DEFINITION_MISMATCH);
        }
        if (payload.rewardType() != record.getRewardType()
                || record.getRewardType() != benefit.getRewardType()) {
            throw mismatch(RewardQuarantineReason.REWARD_TYPE_MISMATCH);
        }
        if (!Objects.equals(payload.rewardFingerprint(), record.getRewardFingerprint())
                || !Objects.equals(record.getRewardFingerprint(), benefit.getRewardFingerprint())) {
            throw mismatch(RewardQuarantineReason.REWARD_FINGERPRINT_MISMATCH);
        }
        if (!Objects.equals(record.getRewardTargetId(), benefit.getRewardTargetId())
                || !Objects.equals(record.getRewardQuantity(), benefit.getRewardQuantity())
                || !Objects.equals(record.getRewardPayload(), benefit.getRewardPayload())
                || benefit.getRewardQuantity() == null || benefit.getRewardQuantity() <= 0
                || benefit.getRewardPayload() == null || benefit.getRewardPayload().isBlank()) {
            throw mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH);
        }
        return new RewardSnapshot(benefit.getRewardDefinitionId(),
                "FROZEN-" + benefit.getRewardDefinitionId(), benefit.getRewardType(),
                benefit.getRewardTargetId(), benefit.getRewardQuantity(),
                benefit.getRewardPayload(), benefit.getRewardFingerprint());
    }

    private RewardIdentityMismatchException mismatch(RewardQuarantineReason reason) {
        return new RewardIdentityMismatchException(reason);
    }
}
