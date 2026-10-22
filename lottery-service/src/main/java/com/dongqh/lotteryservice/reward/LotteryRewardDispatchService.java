package com.dongqh.lotteryservice.reward;

import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.client.PointsClient;
import com.dongqh.hmapi.dto.CouponRewardIssueRequest;
import com.dongqh.hmapi.dto.MembershipRewardGrantRequest;
import com.dongqh.hmapi.dto.PointsRewardCreditRequest;
import com.dongqh.lotteryservice.service.drawchance.DrawChanceReservationService;
import com.dongqh.lotteryservice.domain.po.draworder.DrawOrderRow;
import com.dongqh.lotteryservice.mapper.draworder.DrawOrderMapper;
import com.dongqh.lotteryservice.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lotteryservice.outbox.event.DrawEventEnvelope;
import com.dongqh.lotteryservice.outbox.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.lotteryservice.outbox.mapper.LotteryRewardQuarantineMapper;
import com.dongqh.lotteryservice.outbox.mapper.MessageConsumeRecordMapper;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class LotteryRewardDispatchService {
    private static final String CONSUMER_NAME_DEFAULT = "lottery-service";

    private final DrawOrderMapper orders;
    private final DrawRecordMapper records;
    private final UserBenefitMapper benefits;
    private final LotteryRewardIdentityVerifier identities;
    private final LotteryCurrentRewardResolver currentRewards;
    private final LotteryRewardQuarantineMapper quarantines;
    private final MessageConsumeRecordMapper consumeRecords;
    private final CouponClient coupons;
    private final PointsClient points;
    private final MembershipClient memberships;
    private final DrawChanceReservationService drawChances;
    private final String consumerName;
    private final Duration claimPeriod;

    public LotteryRewardDispatchService(DrawOrderMapper orders, DrawRecordMapper records,
            UserBenefitMapper benefits, LotteryRewardIdentityVerifier identities,
            LotteryCurrentRewardResolver currentRewards,
            LotteryRewardQuarantineMapper quarantines, MessageConsumeRecordMapper consumeRecords,
            CouponClient coupons, PointsClient points, MembershipClient memberships,
            DrawChanceReservationService drawChances,
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
        this.currentRewards = currentRewards;
        this.quarantines = quarantines;
        this.consumeRecords = consumeRecords;
        this.coupons = coupons;
        this.points = points;
        this.memberships = memberships;
        this.drawChances = drawChances;
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
                        benefit == null ? null : benefit.getRewardDefinitionId(), mismatch.reason().name());
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

        CurrentLotteryReward current;
        try {
            current = currentRewards.resolve(reward.prizeId());
        } catch (RewardIdentityMismatchException mismatch) {
            quarantine(eventId, envelope, payload, benefit, mismatch.reason());
            consumeRecords.insertIfAbsent(eventId, consumerName);
            return;
        }
        dispatchCurrent(reward, current);
        consumeRecords.insertIfAbsent(eventId, consumerName);
    }

    private void dispatchCurrent(ValidatedLotteryReward reward, CurrentLotteryReward current) {
        long quantity = current.definition().getQuantity();
        switch (current.definition().getRewardType()) {
            case COUPON -> {
                coupons.issueReward(new CouponRewardIssueRequest(rewardRequestNo(reward.benefitId()),
                        reward.userId(), current.couponTemplate().id(), current.couponTemplate().templateCode(),
                        Math.toIntExact(quantity)));
                transition(reward, BenefitStatus.AVAILABLE);
            }
            case POINTS -> {
                points.creditReward(new PointsRewardCreditRequest(rewardRequestNo(reward.benefitId()),
                        reward.userId(), quantity, "抽奖奖励"));
                transition(reward, BenefitStatus.AVAILABLE);
            }
            case MEMBERSHIP -> {
                int count = Math.toIntExact(quantity);
                memberships.grantReward(new MembershipRewardGrantRequest(rewardRequestNo(reward.benefitId()),
                        reward.userId(), current.membershipProduct().id(), current.membershipProduct().productCode(),
                        current.membershipProduct().durationDays(), count));
                transition(reward, BenefitStatus.AVAILABLE);
            }
            case PRODUCT -> transitionClaimPending(reward);
            case DRAW_CHANCE -> {
                drawChances.credit(reward.userId(), rewardRequestNo(reward.benefitId()), quantity);
                transition(reward, BenefitStatus.AVAILABLE);
            }
        }
    }

    private void quarantine(String eventId, DrawEventEnvelope envelope,
                            PrizeFulfillmentRequestedEvent payload, UserBenefitRow benefit,
                            RewardQuarantineReason reason) {
        quarantines.insertIfAbsent(eventId, envelope.requestId(), envelope.orderId(),
                payload.drawRecordId(), payload.benefitId(), payload.prizeId(),
                benefit == null ? null : benefit.getRewardDefinitionId(), reason.name());
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

    private boolean alreadyConsumed(String eventId) {
        return consumeRecords.countByEventAndConsumer(eventId, consumerName) > 0;
    }

    private String rewardRequestNo(long benefitId) {
        return "LOTTERY-BENEFIT-" + benefitId;
    }
}
