package com.dongqh.lotteryservice.reward;

import com.dongqh.lotteryservice.domain.po.draworder.DrawOrderRow;
import com.dongqh.lotteryservice.enums.draworder.DrawOrderStatus;
import com.dongqh.lotteryservice.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
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
                || !Objects.equals(payload.prizeId(), record.getPrizeId())) {
            throw mismatch(RewardQuarantineReason.RECORD_IDENTITY_MISMATCH);
        }
        if (!Objects.equals(payload.benefitId(), benefit.getId())
                || !Objects.equals(record.getId(), benefit.getDrawRecordId())
                || !Objects.equals(record.getUserId(), benefit.getUserId())
                || !Objects.equals(record.getPrizeId(), benefit.getPrizeId())) {
            throw mismatch(RewardQuarantineReason.BENEFIT_IDENTITY_MISMATCH);
        }

        return new ValidatedLotteryReward(benefit.getId(), record.getId(), order.getId(),
                order.getRequestId(), benefit.getUserId(), order.getActivityId(),
                benefit.getPrizeId(), benefit.getStatus());
    }

    private RewardIdentityMismatchException mismatch(RewardQuarantineReason reason) {
        return new RewardIdentityMismatchException(reason);
    }
}
