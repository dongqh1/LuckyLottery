package com.dongqh.luckyhub.service.lottery.impl;

import com.dongqh.luckyhub.domain.po.benefit.UserBenefit;
import com.dongqh.luckyhub.enums.benefit.BenefitStatus;
import com.dongqh.luckyhub.mapper.benefit.UserBenefitMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.service.inventory.ActivityPrizeInventoryService;
import com.dongqh.luckyhub.domain.algorithm.lottery.DrawCandidate;
import com.dongqh.luckyhub.domain.algorithm.lottery.PrizeWeightSnapshot;
import com.dongqh.luckyhub.domain.algorithm.lottery.WeightedDrawEngine;
import com.dongqh.luckyhub.domain.po.lottery.LotteryDrawRecord;
import com.dongqh.luckyhub.enums.lottery.DrawOrderStatus;
import com.dongqh.luckyhub.enums.lottery.DrawResultType;
import com.dongqh.luckyhub.enums.lottery.LotteryErrorCode;
import com.dongqh.luckyhub.mapper.lottery.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.mapper.lottery.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.messaging.lottery.event.DrawConfirmedEvent;
import com.dongqh.luckyhub.messaging.lottery.event.DrawEventEnvelope;
import com.dongqh.luckyhub.messaging.lottery.event.DrawEventType;
import com.dongqh.luckyhub.messaging.lottery.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.luckyhub.domain.model.lottery.DrawExecutionContext;
import com.dongqh.luckyhub.domain.model.lottery.DrawExecutionResult;
import com.dongqh.luckyhub.domain.model.lottery.DrawPrizeSnapshot;
import com.dongqh.luckyhub.domain.model.lottery.DrawResultItem;
import com.dongqh.luckyhub.domain.model.reward.RewardSnapshot;
import com.dongqh.luckyhub.service.lottery.DrawTransactionService;
import com.dongqh.luckyhub.service.lottery.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class DrawTransactionServiceImpl implements DrawTransactionService {

    private final WeightedDrawEngine drawEngine;
    private final ActivityPrizeInventoryService inventoryService;
    private final LotteryDrawOrderMapper orderMapper;
    private final LotteryDrawRecordMapper recordMapper;
    private final UserBenefitMapper benefitMapper;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public DrawTransactionServiceImpl(
            WeightedDrawEngine drawEngine,
            ActivityPrizeInventoryService inventoryService,
            LotteryDrawOrderMapper orderMapper,
            LotteryDrawRecordMapper recordMapper,
            UserBenefitMapper benefitMapper,
            OutboxService outboxService,
            ObjectMapper objectMapper) {
        this.drawEngine = drawEngine;
        this.inventoryService = inventoryService;
        this.orderMapper = orderMapper;
        this.recordMapper = recordMapper;
        this.benefitMapper = benefitMapper;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public DrawExecutionResult execute(DrawExecutionContext context) {
        List<PrizeWeightSnapshot> weights = context.prizes().stream()
                .map(DrawPrizeSnapshot::toWeightSnapshot)
                .toList();
        List<DrawResultItem> results = new ArrayList<>(context.drawCount());

        for (int sequence = 1; sequence <= context.drawCount(); sequence++) {
            DrawCandidate candidate = drawEngine.select(weights, context.noWinWeight());
            DrawPrizeSnapshot wonPrize = resolveFinalWin(candidate, context.prizes());
            results.add(persistResult(context, sequence, wonPrize));
        }

        if (orderMapper.markSuccessIfProcessing(context.orderId(), context.drawTime()) != 1) {
            throw new BusinessException(LotteryErrorCode.DRAW_TRANSACTION_FAILED);
        }

        appendConfirmedEvent(context);
        results.stream()
                .filter(item -> item.resultType() == DrawResultType.WIN)
                .forEach(item -> appendFulfillmentEvent(context, item));

        return new DrawExecutionResult(
                context.orderId(), context.requestId(), DrawOrderStatus.SUCCESS,
                context.drawTime(), results);
    }

    private DrawPrizeSnapshot resolveFinalWin(
            DrawCandidate candidate,
            List<DrawPrizeSnapshot> snapshots) {
        if (candidate.type() == DrawCandidate.Type.NO_WIN) {
            return null;
        }
        DrawPrizeSnapshot snapshot = snapshots.stream()
                .filter(prize -> prize.activityPrizeId() == candidate.activityPrizeId()
                        && prize.prizeId() == candidate.prizeId())
                .findFirst()
                .orElseThrow(() -> new BusinessException(LotteryErrorCode.DRAW_TRANSACTION_FAILED));
        return inventoryService.decrementIfAvailable(snapshot.activityPrizeId()) ? snapshot : null;
    }

    private DrawResultItem persistResult(
            DrawExecutionContext context,
            int sequence,
            DrawPrizeSnapshot prize) {
        LotteryDrawRecord record = new LotteryDrawRecord();
        record.setOrderId(context.orderId());
        record.setRequestId(context.requestId());
        record.setSequenceNo(sequence);
        record.setUserId(context.userId());
        record.setActivityId(context.activityId());
        record.setResultType(prize == null ? DrawResultType.NO_WIN : DrawResultType.WIN);
        record.setDrawTime(context.drawTime());
        if (prize != null) {
            record.setPrizeId(prize.prizeId());
            record.setPrizeName(prize.prizeName());
            record.setPrizeType(prize.prizeType());
            record.setPrizeImageUrl(prize.prizeImageUrl());
            copyReward(prize.rewardSnapshot(), record);
        }
        recordMapper.insert(record);

        Long benefitId = prize == null ? null : persistPendingBenefit(context, record, prize);
        return new DrawResultItem(
                record.getId(), sequence, record.getResultType(), record.getPrizeId(),
                record.getPrizeName(), record.getPrizeType(), record.getPrizeImageUrl(), benefitId,
                prize == null ? null : prize.rewardSnapshot());
    }

    private Long persistPendingBenefit(
            DrawExecutionContext context,
            LotteryDrawRecord record,
            DrawPrizeSnapshot prize) {
        UserBenefit benefit = new UserBenefit();
        benefit.setDrawRecordId(record.getId());
        benefit.setUserId(context.userId());
        benefit.setPrizeId(prize.prizeId());
        benefit.setPrizeType(prize.prizeType());
        copyReward(prize.rewardSnapshot(), benefit);
        benefit.setQuantity(1);
        benefit.setStatus(BenefitStatus.PENDING);
        benefit.setObtainedAt(context.drawTime());
        benefitMapper.insert(benefit);
        return benefit.getId();
    }

    private void copyReward(RewardSnapshot reward, LotteryDrawRecord record) {
        if (reward == null) return;
        record.setRewardDefinitionId(reward.rewardDefinitionId());
        record.setRewardType(reward.rewardType());
        record.setRewardTargetId(reward.targetId());
        record.setRewardQuantity(reward.quantity());
        record.setRewardPayload(reward.payloadJson());
        record.setRewardFingerprint(reward.fingerprint());
    }

    private void copyReward(RewardSnapshot reward, UserBenefit benefit) {
        if (reward == null) return;
        benefit.setRewardDefinitionId(reward.rewardDefinitionId());
        benefit.setRewardType(reward.rewardType());
        benefit.setRewardTargetId(reward.targetId());
        benefit.setRewardQuantity(reward.quantity());
        benefit.setRewardPayload(reward.payloadJson());
        benefit.setRewardFingerprint(reward.fingerprint());
    }

    private void appendConfirmedEvent(DrawExecutionContext context) {
        outboxService.append(DrawEventEnvelope.create(
                DrawEventType.DRAW_CONFIRMED,
                context.requestId(), context.userId(), context.activityId(), context.orderId(),
                context.drawTime(), new DrawConfirmedEvent(context.drawCount(), context.drawDate()),
                objectMapper));
    }

    private void appendFulfillmentEvent(DrawExecutionContext context, DrawResultItem item) {
        outboxService.append(DrawEventEnvelope.create(
                DrawEventType.PRIZE_FULFILLMENT_REQUESTED,
                context.requestId(), context.userId(), context.activityId(), context.orderId(),
                context.drawTime(), new PrizeFulfillmentRequestedEvent(
                        item.benefitId(), item.recordId(), item.prizeId(), item.prizeType(),
                        item.rewardSnapshot() == null ? null : item.rewardSnapshot().rewardDefinitionId(),
                        item.rewardSnapshot() == null ? null : item.rewardSnapshot().rewardType(),
                        item.rewardSnapshot() == null ? null : item.rewardSnapshot().fingerprint()),
                objectMapper));
    }
}
