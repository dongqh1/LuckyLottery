package com.dongqh.luckyhub.service.lottery.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.enums.benefit.BenefitErrorCode;
import com.dongqh.luckyhub.enums.benefit.BenefitStatus;
import com.dongqh.luckyhub.mapper.benefit.UserBenefitMapper;
import com.dongqh.luckyhub.service.benefit.BenefitFulfillmentService;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.service.drawchance.DrawChanceService;
import com.dongqh.luckyhub.domain.dto.fulfillment.CreateFulfillmentTaskCommand;
import com.dongqh.luckyhub.enums.fulfillment.FulfillmentType;
import com.dongqh.luckyhub.domain.model.fulfillment.CouponFulfillmentPayload;
import com.dongqh.luckyhub.domain.model.fulfillment.MembershipFulfillmentPayload;
import com.dongqh.luckyhub.domain.model.fulfillment.PointsFulfillmentPayload;
import com.dongqh.luckyhub.service.fulfillment.FulfillmentTaskService;
import com.dongqh.luckyhub.config.lottery.MessagingProperties;
import com.dongqh.luckyhub.domain.po.lottery.LotteryRewardQuarantine;
import com.dongqh.luckyhub.domain.po.lottery.MessageConsumeRecord;
import com.dongqh.luckyhub.enums.lottery.RewardQuarantineStatus;
import com.dongqh.luckyhub.mapper.lottery.LotteryRewardQuarantineMapper;
import com.dongqh.luckyhub.mapper.lottery.MessageConsumeRecordMapper;
import com.dongqh.luckyhub.messaging.lottery.event.DrawEventEnvelope;
import com.dongqh.luckyhub.messaging.lottery.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.domain.model.lottery.ValidatedLotteryReward;
import com.dongqh.luckyhub.service.lottery.LotteryRewardDispatchService;
import com.dongqh.luckyhub.service.lottery.LotteryRewardIdentityService;
import com.dongqh.luckyhub.service.lottery.RewardIdentityMismatchException;
import com.dongqh.luckyhub.domain.model.reward.CouponRewardPayload;
import com.dongqh.luckyhub.domain.model.reward.MembershipRewardPayload;
import com.dongqh.luckyhub.domain.model.reward.PointsRewardPayload;
import com.dongqh.luckyhub.config.shipping.ShippingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Service
public class LotteryRewardDispatchServiceImpl implements LotteryRewardDispatchService {
    private final LotteryRewardIdentityService identities;
    private final BenefitFulfillmentService legacyFulfillment;
    private final FulfillmentTaskService tasks;
    private final DrawChanceService drawChances;
    private final UserBenefitMapper benefits;
    private final LotteryRewardQuarantineMapper quarantines;
    private final MessageConsumeRecordMapper consumeRecords;
    private final ObjectMapper json;
    private final String consumerName;
    private final Duration claimPeriod;

    @Autowired
    public LotteryRewardDispatchServiceImpl(LotteryRewardIdentityService identities,
            BenefitFulfillmentService legacyFulfillment, FulfillmentTaskService tasks,
            DrawChanceService drawChances, UserBenefitMapper benefits,
            LotteryRewardQuarantineMapper quarantines, MessageConsumeRecordMapper consumeRecords,
            ObjectMapper json, MessagingProperties properties, ShippingProperties shippingProperties) {
        this.identities = identities; this.legacyFulfillment = legacyFulfillment;
        this.tasks = tasks; this.drawChances = drawChances; this.benefits = benefits;
        this.quarantines = quarantines; this.consumeRecords = consumeRecords;
        this.json = json; this.consumerName = properties.logicalConsumerName();
        this.claimPeriod = shippingProperties.claimPeriod();
    }

    public LotteryRewardDispatchServiceImpl(LotteryRewardIdentityService identities,
            BenefitFulfillmentService legacyFulfillment, FulfillmentTaskService tasks,
            DrawChanceService drawChances, UserBenefitMapper benefits,
            LotteryRewardQuarantineMapper quarantines, MessageConsumeRecordMapper consumeRecords,
            ObjectMapper json, MessagingProperties properties) {
        this.identities = identities; this.legacyFulfillment = legacyFulfillment;
        this.tasks = tasks; this.drawChances = drawChances; this.benefits = benefits;
        this.quarantines = quarantines; this.consumeRecords = consumeRecords;
        this.json = json; this.consumerName = properties.logicalConsumerName();
        this.claimPeriod = Duration.ofDays(7);
    }

    @Override
    @Transactional
    public void dispatch(DrawEventEnvelope envelope, PrizeFulfillmentRequestedEvent payload) {
        String eventId = envelope.eventId().toString();
        ValidatedLotteryReward reward;
        try {
            reward = identities.validate(envelope, payload);
        } catch (RewardIdentityMismatchException mismatch) {
            if (alreadyConsumed(eventId)) return;
            quarantine(envelope, payload, mismatch);
            recordConsumed(eventId);
            return;
        }
        // Identity validation locks the benefit row. Recheck after that lock so concurrent
        // deliveries serialize before creating a task or writing any reward side effect.
        if (alreadyConsumed(eventId)) return;
        if (reward.rewardSnapshot() == null) {
            legacyFulfillment.fulfill(reward.benefitId(), eventId);
            return;
        }
        if (reward.benefitStatus() != BenefitStatus.PENDING
                && reward.benefitStatus() != BenefitStatus.GRANT_FAILED) {
            throw new BusinessException(BenefitErrorCode.BENEFIT_STATE_CONFLICT);
        }

        String fulfillmentNo = "LOTTERY-BENEFIT-" + reward.benefitId();
        switch (reward.rewardSnapshot().rewardType()) {
            case COUPON -> {
                CouponRewardPayload frozen = couponPayload(reward);
                createTask(reward, fulfillmentNo, FulfillmentType.COUPON,
                        new CouponFulfillmentPayload(frozen.templateCode(), frozen.quantity()));
            }
            case POINTS -> {
                PointsRewardPayload frozen = pointsPayload(reward);
                createTask(reward, fulfillmentNo, FulfillmentType.POINTS,
                        new PointsFulfillmentPayload(frozen.points(), frozen.reason()));
            }
            case MEMBERSHIP -> {
                MembershipRewardPayload frozen = membershipPayload(reward);
                createTask(reward, fulfillmentNo, FulfillmentType.MEMBERSHIP,
                        new MembershipFulfillmentPayload(frozen.productCode(), frozen.durationDays()));
            }
            case PRODUCT -> transitionProduct(reward);
            case DRAW_CHANCE -> {
                drawChances.credit(reward.userId(), fulfillmentNo, reward.rewardSnapshot().quantity());
                transition(reward, BenefitStatus.AVAILABLE);
            }
        }
        recordConsumed(eventId);
    }

    private void createTask(ValidatedLotteryReward reward, String fulfillmentNo,
                            FulfillmentType type,
                            com.dongqh.luckyhub.domain.model.fulfillment.FulfillmentPayload payload) {
        tasks.create(new CreateFulfillmentTaskCommand(fulfillmentNo, "LOTTERY_BENEFIT",
                Long.toString(reward.benefitId()), type, reward.userId(), payload, null));
        benefits.bindFulfillmentNo(reward.benefitId(), fulfillmentNo);
    }

    private CouponRewardPayload couponPayload(ValidatedLotteryReward reward) {
        return read(reward, CouponRewardPayload.class);
    }
    private PointsRewardPayload pointsPayload(ValidatedLotteryReward reward) {
        return read(reward, PointsRewardPayload.class);
    }
    private MembershipRewardPayload membershipPayload(ValidatedLotteryReward reward) {
        return read(reward, MembershipRewardPayload.class);
    }
    private <T> T read(ValidatedLotteryReward reward, Class<T> type) {
        try { return json.readValue(reward.rewardSnapshot().payloadJson(), type); }
        catch (JacksonException error) { throw new IllegalStateException("冻结奖励载荷无法解析", error); }
    }

    private void transition(ValidatedLotteryReward reward, BenefitStatus target) {
        if (benefits.transitionStatus(reward.benefitId(), reward.benefitStatus(), target) != 1) {
            throw new BusinessException(BenefitErrorCode.BENEFIT_STATE_CONFLICT);
        }
    }

    private void transitionProduct(ValidatedLotteryReward reward) {
        LocalDateTime deadline = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS).plus(claimPeriod);
        if (benefits.markClaimPending(reward.benefitId(), reward.benefitStatus(), deadline) != 1) {
            throw new BusinessException(BenefitErrorCode.BENEFIT_STATE_CONFLICT);
        }
    }

    private void quarantine(DrawEventEnvelope envelope, PrizeFulfillmentRequestedEvent payload,
                            RewardIdentityMismatchException mismatch) {
        LotteryRewardQuarantine row = new LotteryRewardQuarantine();
        row.setEventId(envelope.eventId().toString()); row.setRequestId(envelope.requestId());
        row.setOrderId(envelope.orderId()); row.setDrawRecordId(payload.drawRecordId());
        row.setBenefitId(payload.benefitId()); row.setPrizeId(payload.prizeId());
        row.setRewardDefinitionId(payload.rewardDefinitionId()); row.setReasonCode(mismatch.reason().name());
        row.setStatus(RewardQuarantineStatus.OPEN); row.setQuarantinedAt(LocalDateTime.now());
        quarantines.insert(row);
    }

    private boolean alreadyConsumed(String eventId) {
        return consumeRecords.selectCount(new LambdaQueryWrapper<MessageConsumeRecord>()
                .eq(MessageConsumeRecord::getEventId, eventId)
                .eq(MessageConsumeRecord::getConsumerName, consumerName)) > 0;
    }

    private void recordConsumed(String eventId) {
        MessageConsumeRecord record = new MessageConsumeRecord();
        record.setEventId(eventId); record.setConsumerName(consumerName);
        record.setConsumedAt(LocalDateTime.now()); consumeRecords.insert(record);
    }
}
