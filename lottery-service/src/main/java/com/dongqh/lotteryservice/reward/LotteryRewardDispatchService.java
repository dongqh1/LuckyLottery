package com.dongqh.lotteryservice.reward;

import com.dongqh.hmapi.client.FulfillmentClient;
import com.dongqh.hmapi.dto.CreateFulfillmentTaskRequest;
import com.dongqh.hmapi.dto.FulfillmentCouponTaskPayload;
import com.dongqh.hmapi.dto.FulfillmentMembershipTaskPayload;
import com.dongqh.hmapi.dto.FulfillmentPointsTaskPayload;
import com.dongqh.hmapi.dto.FulfillmentTaskDTO;
import com.dongqh.lotteryservice.service.drawchance.DrawChanceReservationService;
import com.dongqh.lotteryservice.domain.po.draworder.DrawOrderRow;
import com.dongqh.lotteryservice.mapper.draworder.DrawOrderMapper;
import com.dongqh.lotteryservice.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lotteryservice.domain.model.drawtransaction.CouponRewardPayload;
import com.dongqh.lotteryservice.domain.model.drawtransaction.MembershipRewardPayload;
import com.dongqh.lotteryservice.domain.model.drawtransaction.PointsRewardPayload;
import com.dongqh.lotteryservice.domain.model.drawtransaction.RewardSnapshot;
import com.dongqh.lotteryservice.outbox.event.DrawEventEnvelope;
import com.dongqh.lotteryservice.outbox.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.lotteryservice.outbox.mapper.LotteryRewardQuarantineMapper;
import com.dongqh.lotteryservice.outbox.mapper.MessageConsumeRecordMapper;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class LotteryRewardDispatchService {
    private static final String CONSUMER_NAME_DEFAULT = "lottery-service";
    private static final String FULFILLMENT_SOURCE = "LOTTERY_BENEFIT";

    private final DrawOrderMapper orders;
    private final DrawRecordMapper records;
    private final UserBenefitMapper benefits;
    private final LotteryRewardIdentityVerifier identities;
    private final LotteryRewardQuarantineMapper quarantines;
    private final MessageConsumeRecordMapper consumeRecords;
    private final FulfillmentClient fulfillment;
    private final DrawChanceReservationService drawChances;
    private final ObjectMapper json;
    private final String consumerName;
    private final Duration claimPeriod;

    public LotteryRewardDispatchService(DrawOrderMapper orders, DrawRecordMapper records,
            UserBenefitMapper benefits, LotteryRewardIdentityVerifier identities,
            LotteryRewardQuarantineMapper quarantines, MessageConsumeRecordMapper consumeRecords,
            FulfillmentClient fulfillment, DrawChanceReservationService drawChances, ObjectMapper json,
            @Value("${luckyhub.lottery.consumer-name:lottery-service}") String consumerName,
            @Value("${luckyhub.lottery.claim-period:7d}") Duration claimPeriod) {
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName must not be blank");
        }
        if (claimPeriod == null || claimPeriod.isZero() || claimPeriod.isNegative()) {
            throw new IllegalArgumentException("claimPeriod must be positive");
        }
        this.orders = orders;
        this.records = records;
        this.benefits = benefits;
        this.identities = identities;
        this.quarantines = quarantines;
        this.consumeRecords = consumeRecords;
        this.fulfillment = fulfillment;
        this.drawChances = drawChances;
        this.json = json;
        this.consumerName = consumerName;
        this.claimPeriod = claimPeriod;
    }

    @Transactional
    public void dispatch(DrawEventEnvelope envelope, PrizeFulfillmentRequestedEvent payload) {
        String eventId = envelope.eventId().toString();
        UserBenefitRow benefit = benefits.selectByIdForUpdate(payload.benefitId());
        DrawRecordRow record = records.selectById(payload.drawRecordId());
        DrawOrderRow order = orders.selectById(envelope.orderId());

        ValidatedLotteryReward reward;
        try {
            reward = identities.validate(envelope, payload, order, record, benefit);
        } catch (RewardIdentityMismatchException mismatch) {
            if (!alreadyConsumed(eventId)) {
                quarantines.insertIfAbsent(eventId, envelope.requestId(), envelope.orderId(),
                        payload.drawRecordId(), payload.benefitId(), payload.prizeId(),
                        payload.rewardDefinitionId(), mismatch.reason().name());
                consumeRecords.insertIfAbsent(eventId, consumerName);
            }
            return;
        }
        if (alreadyConsumed(eventId)) {
            return;
        }
        if (reward.benefitStatus() != BenefitStatus.PENDING
                && reward.benefitStatus() != BenefitStatus.GRANT_FAILED) {
            throw new IllegalStateException("Lottery reward is not pending fulfillment");
        }

        RewardSnapshot snapshot = reward.rewardSnapshot();
        if (snapshot == null) {
            transitionLegacyBenefit(reward, benefit);
        } else {
            dispatchSnapshot(reward, benefit, snapshot);
        }
        consumeRecords.insertIfAbsent(eventId, consumerName);
    }

    private void dispatchSnapshot(ValidatedLotteryReward reward, UserBenefitRow benefit,
                                  RewardSnapshot snapshot) {
        switch (snapshot.rewardType()) {
            case COUPON -> {
                CouponRewardPayload frozen = read(snapshot, CouponRewardPayload.class);
                createFulfillment(reward, benefit, "COUPON",
                        new FulfillmentCouponTaskPayload(frozen.templateCode(), frozen.quantity(), frozen.templateId()));
            }
            case POINTS -> {
                PointsRewardPayload frozen = read(snapshot, PointsRewardPayload.class);
                createFulfillment(reward, benefit, "POINTS",
                        new FulfillmentPointsTaskPayload(frozen.points(), frozen.reason()));
            }
            case MEMBERSHIP -> {
                MembershipRewardPayload frozen = read(snapshot, MembershipRewardPayload.class);
                createFulfillment(reward, benefit, "MEMBERSHIP",
                        new FulfillmentMembershipTaskPayload(frozen.productCode(), frozen.durationDays(),
                                frozen.quantity(), frozen.membershipProductId()));
            }
            case PRODUCT -> transitionClaimPending(reward);
            case DRAW_CHANCE -> {
                drawChances.credit(reward.userId(), fulfillmentNo(reward.benefitId()), snapshot.quantity());
                transition(reward, BenefitStatus.AVAILABLE);
            }
        }
    }

    private void transitionLegacyBenefit(ValidatedLotteryReward reward, UserBenefitRow benefit) {
        if (benefit.getPrizeType() == PrizeType.PHYSICAL) {
            transitionClaimPending(reward);
        } else {
            transition(reward, BenefitStatus.AVAILABLE);
        }
    }

    private void createFulfillment(ValidatedLotteryReward reward, UserBenefitRow benefit,
                                   String type, Object payload) {
        String fulfillmentNo = fulfillmentNo(reward.benefitId());
        FulfillmentTaskDTO created = fulfillment.createTask(new CreateFulfillmentTaskRequest(
                fulfillmentNo, FULFILLMENT_SOURCE, Long.toString(reward.benefitId()), type,
                reward.userId(), write(payload), null));
        if (created == null || !fulfillmentNo.equals(created.fulfillmentNo())
                || !FULFILLMENT_SOURCE.equals(created.sourceType())
                || !Long.toString(reward.benefitId()).equals(created.sourceId())
                || !Long.valueOf(reward.userId()).equals(created.targetUserId())
                || !type.equals(created.fulfillmentType())) {
            throw new IllegalStateException("Fulfillment returned a mismatched task identity");
        }
        String boundNo = benefit.getFulfillmentNo();
        if (boundNo != null && !boundNo.equals(fulfillmentNo)) {
            throw new IllegalStateException("Lottery benefit is bound to another fulfillment task");
        }
        if (boundNo == null && benefits.bindFulfillmentNo(reward.benefitId(), fulfillmentNo) != 1) {
            throw new IllegalStateException("Lottery benefit fulfillment task could not be bound");
        }
    }

    private void transitionClaimPending(ValidatedLotteryReward reward) {
        if (benefits.markClaimPending(reward.benefitId(), reward.benefitStatus(),
                LocalDateTime.now().plus(claimPeriod)) != 1) {
            throw new IllegalStateException("Lottery physical benefit state changed concurrently");
        }
    }

    private void transition(ValidatedLotteryReward reward, BenefitStatus target) {
        if (benefits.transitionStatus(reward.benefitId(), reward.benefitStatus(), target) != 1) {
            throw new IllegalStateException("Lottery benefit state changed concurrently");
        }
    }

    private <T> T read(RewardSnapshot snapshot, Class<T> payloadType) {
        try {
            return json.readValue(snapshot.payloadJson(), payloadType);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Frozen Lottery reward payload is invalid", exception);
        }
    }

    private String write(Object payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Fulfillment payload could not be serialized", exception);
        }
    }

    private boolean alreadyConsumed(String eventId) {
        return consumeRecords.countByEventAndConsumer(eventId, consumerName) > 0;
    }

    private String fulfillmentNo(long benefitId) {
        return "LOTTERY-BENEFIT-" + benefitId;
    }
}
