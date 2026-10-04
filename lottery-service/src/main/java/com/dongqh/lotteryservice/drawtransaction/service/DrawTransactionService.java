package com.dongqh.lotteryservice.drawtransaction.service;

import com.dongqh.lotteryservice.algorithm.DrawCandidate;
import com.dongqh.lotteryservice.algorithm.PrizeWeightSnapshot;
import com.dongqh.lotteryservice.algorithm.WeightedDrawEngine;
import com.dongqh.lotteryservice.draworder.enums.DrawOrderStatus;
import com.dongqh.lotteryservice.draworder.mapper.DrawOrderMapper;
import com.dongqh.lotteryservice.drawtransaction.entity.DrawRecordRow;
import com.dongqh.lotteryservice.drawtransaction.entity.UserBenefitRow;
import com.dongqh.lotteryservice.drawtransaction.enums.BenefitStatus;
import com.dongqh.lotteryservice.drawtransaction.enums.DrawResultType;
import com.dongqh.lotteryservice.drawtransaction.mapper.ActivityPrizeInventoryMapper;
import com.dongqh.lotteryservice.drawtransaction.mapper.DrawRecordMapper;
import com.dongqh.lotteryservice.drawtransaction.mapper.UserBenefitMapper;
import com.dongqh.lotteryservice.drawtransaction.model.DrawExecutionContext;
import com.dongqh.lotteryservice.drawtransaction.model.DrawExecutionResult;
import com.dongqh.lotteryservice.drawtransaction.model.DrawPrizeSnapshot;
import com.dongqh.lotteryservice.drawtransaction.model.DrawResultItem;
import com.dongqh.lotteryservice.drawtransaction.model.RewardSnapshot;
import com.dongqh.lotteryservice.domain.LotteryDomainException;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import com.dongqh.lotteryservice.outbox.event.DrawConfirmedEvent;
import com.dongqh.lotteryservice.outbox.event.DrawEventEnvelope;
import com.dongqh.lotteryservice.outbox.event.DrawEventType;
import com.dongqh.lotteryservice.outbox.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.lotteryservice.outbox.service.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class DrawTransactionService {
    private final WeightedDrawEngine drawEngine;
    private final ActivityPrizeInventoryMapper inventoryMapper;
    private final DrawOrderMapper orderMapper;
    private final DrawRecordMapper recordMapper;
    private final UserBenefitMapper benefitMapper;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public DrawTransactionService(WeightedDrawEngine drawEngine, ActivityPrizeInventoryMapper inventoryMapper,
                                  DrawOrderMapper orderMapper, DrawRecordMapper recordMapper,
                                  UserBenefitMapper benefitMapper, OutboxService outboxService,
                                  ObjectMapper objectMapper) {
        this.drawEngine = drawEngine;
        this.inventoryMapper = inventoryMapper;
        this.orderMapper = orderMapper;
        this.recordMapper = recordMapper;
        this.benefitMapper = benefitMapper;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

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
            throw new LotteryDomainException(LotteryErrorCode.DRAW_TRANSACTION_FAILED);
        }

        appendConfirmedEvent(context);
        results.stream()
                .filter(item -> item.resultType() == DrawResultType.WIN)
                .forEach(item -> appendFulfillmentEvent(context, item));

        return new DrawExecutionResult(context.orderId(), context.requestId(), DrawOrderStatus.SUCCESS,
                context.drawTime(), results);
    }

    private DrawPrizeSnapshot resolveFinalWin(DrawCandidate candidate, List<DrawPrizeSnapshot> snapshots) {
        if (candidate.type() == DrawCandidate.Type.NO_WIN) {
            return null;
        }
        DrawPrizeSnapshot snapshot = snapshots.stream()
                .filter(prize -> prize.activityPrizeId() == candidate.activityPrizeId()
                        && prize.prizeId() == candidate.prizeId())
                .findFirst()
                .orElseThrow(() -> new LotteryDomainException(LotteryErrorCode.DRAW_TRANSACTION_FAILED));
        return inventoryMapper.decrementIfAvailable(snapshot.activityPrizeId()) == 1 ? snapshot : null;
    }

    private DrawResultItem persistResult(DrawExecutionContext context, int sequence, DrawPrizeSnapshot prize) {
        DrawRecordRow record = new DrawRecordRow();
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
        return new DrawResultItem(record.getId(), sequence, record.getResultType(), record.getPrizeId(),
                record.getPrizeName(), record.getPrizeType(), record.getPrizeImageUrl(), benefitId,
                prize == null ? null : prize.rewardSnapshot());
    }

    private Long persistPendingBenefit(DrawExecutionContext context, DrawRecordRow record,
                                      DrawPrizeSnapshot prize) {
        UserBenefitRow benefit = new UserBenefitRow();
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

    private void copyReward(RewardSnapshot reward, DrawRecordRow record) {
        if (reward == null) return;
        record.setRewardDefinitionId(reward.rewardDefinitionId());
        record.setRewardType(reward.rewardType());
        record.setRewardTargetId(reward.targetId());
        record.setRewardQuantity(reward.quantity());
        record.setRewardPayload(reward.payloadJson());
        record.setRewardFingerprint(reward.fingerprint());
    }

    private void copyReward(RewardSnapshot reward, UserBenefitRow benefit) {
        if (reward == null) return;
        benefit.setRewardDefinitionId(reward.rewardDefinitionId());
        benefit.setRewardType(reward.rewardType());
        benefit.setRewardTargetId(reward.targetId());
        benefit.setRewardQuantity(reward.quantity());
        benefit.setRewardPayload(reward.payloadJson());
        benefit.setRewardFingerprint(reward.fingerprint());
    }

    private void appendConfirmedEvent(DrawExecutionContext context) {
        outboxService.append(DrawEventEnvelope.create(DrawEventType.DRAW_CONFIRMED,
                context.requestId(), context.userId(), context.activityId(), context.orderId(),
                context.drawTime(), new DrawConfirmedEvent(context.drawCount(), context.drawDate()), objectMapper));
    }

    private void appendFulfillmentEvent(DrawExecutionContext context, DrawResultItem item) {
        RewardSnapshot reward = item.rewardSnapshot();
        outboxService.append(DrawEventEnvelope.create(DrawEventType.PRIZE_FULFILLMENT_REQUESTED,
                context.requestId(), context.userId(), context.activityId(), context.orderId(), context.drawTime(),
                new PrizeFulfillmentRequestedEvent(item.benefitId(), item.recordId(), item.prizeId(), item.prizeType(),
                        reward == null ? null : reward.rewardDefinitionId(),
                        reward == null ? null : reward.rewardType(),
                        reward == null ? null : reward.fingerprint()), objectMapper));
    }
}
