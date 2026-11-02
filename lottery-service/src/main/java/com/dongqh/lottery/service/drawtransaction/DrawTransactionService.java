package com.dongqh.lottery.service.drawtransaction;

import com.dongqh.lottery.algorithm.DrawCandidate;
import com.dongqh.lottery.algorithm.WeightedPrize;
import com.dongqh.lottery.algorithm.WeightedDrawEngine;
import com.dongqh.lottery.enums.draworder.DrawOrderStatus;
import com.dongqh.lottery.mapper.draworder.DrawOrderMapper;
import com.dongqh.lottery.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lottery.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lottery.enums.drawtransaction.BenefitStatus;
import com.dongqh.lottery.enums.drawtransaction.DrawResultType;
import com.dongqh.lottery.mapper.drawtransaction.ActivityPrizeInventoryMapper;
import com.dongqh.lottery.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lottery.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lottery.domain.model.drawtransaction.DrawExecutionContext;
import com.dongqh.lottery.domain.model.drawtransaction.DrawExecutionResult;
import com.dongqh.lottery.domain.model.drawtransaction.DrawPrizeCandidate;
import com.dongqh.lottery.domain.model.drawtransaction.DrawResultItem;
import com.dongqh.lottery.enums.prizecatalog.RewardType;
import com.dongqh.lottery.exception.LotteryDomainException;
import com.dongqh.lottery.enums.LotteryErrorCode;
import com.dongqh.lottery.outbox.event.DrawConfirmedEvent;
import com.dongqh.lottery.outbox.event.DrawEventEnvelope;
import com.dongqh.lottery.outbox.event.DrawEventType;
import com.dongqh.lottery.outbox.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.lottery.outbox.service.OutboxService;
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
        List<WeightedPrize> weights = context.prizes().stream()
                .map(DrawPrizeCandidate::toWeightedPrize)
                .toList();
        List<DrawResultItem> results = new ArrayList<>(context.drawCount());

        for (int sequence = 1; sequence <= context.drawCount(); sequence++) {

            DrawCandidate candidate = drawEngine.select(weights, context.noWinWeight());

            DrawPrizeCandidate wonPrize = resolveFinalWin(candidate, context.prizes());

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

    private DrawPrizeCandidate resolveFinalWin(DrawCandidate candidate, List<DrawPrizeCandidate> candidates) {
        if (candidate.type() == DrawCandidate.Type.NO_WIN) {
            return null;
        }
        DrawPrizeCandidate selectedCandidate = candidates.stream()
                .filter(prize -> prize.activityPrizeId() == candidate.activityPrizeId()
                        && prize.prizeId() == candidate.prizeId())
                .findFirst()
                .orElseThrow(() -> new LotteryDomainException(LotteryErrorCode.DRAW_TRANSACTION_FAILED));
        return inventoryMapper.decrementIfAvailable(selectedCandidate.activityPrizeId()) == 1
                ? selectedCandidate : null;
    }

    private DrawResultItem persistResult(DrawExecutionContext context, int sequence, DrawPrizeCandidate prize) {
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
        }
        recordMapper.insert(record);

        Long benefitId = prize == null ? null : persistPendingBenefit(context, record, prize);
        return new DrawResultItem(record.getId(), sequence, record.getResultType(), record.getPrizeId(),
                prize == null ? null : prize.prizeName(), prize == null ? null : prize.prizeType(),
                prize == null ? null : prize.prizeImageUrl(), benefitId,
                prize != null && prize.rewardType() == RewardType.PRODUCT ? prize.rewardTargetId() : null);
    }

    private Long persistPendingBenefit(DrawExecutionContext context, DrawRecordRow record,
                                      DrawPrizeCandidate prize) {
        UserBenefitRow benefit = new UserBenefitRow();
        benefit.setDrawRecordId(record.getId());
        benefit.setUserId(context.userId());
        benefit.setPrizeId(prize.prizeId());
        benefit.setQuantity(1);
        benefit.setStatus(BenefitStatus.PENDING);
        benefit.setObtainedAt(context.drawTime());
        benefitMapper.insert(benefit);
        return benefit.getId();
    }

    private void appendConfirmedEvent(DrawExecutionContext context) {
        outboxService.append(DrawEventEnvelope.create(DrawEventType.DRAW_CONFIRMED,
                context.requestId(), context.userId(), context.activityId(), context.orderId(),
                context.drawTime(), new DrawConfirmedEvent(context.drawCount(), context.drawDate()), objectMapper));
    }

    private void appendFulfillmentEvent(DrawExecutionContext context, DrawResultItem item) {
        outboxService.append(DrawEventEnvelope.create(DrawEventType.PRIZE_FULFILLMENT_REQUESTED,
                context.requestId(), context.userId(), context.activityId(), context.orderId(), context.drawTime(),
                new PrizeFulfillmentRequestedEvent(item.benefitId(), item.recordId(), item.prizeId()), objectMapper));
    }
}
