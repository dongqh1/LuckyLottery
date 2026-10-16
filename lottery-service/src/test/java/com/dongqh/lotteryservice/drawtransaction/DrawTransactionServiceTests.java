package com.dongqh.lotteryservice.drawtransaction;

import com.dongqh.lotteryservice.algorithm.DrawCandidate;
import com.dongqh.lotteryservice.algorithm.PrizeWeightSnapshot;
import com.dongqh.lotteryservice.algorithm.WeightedDrawEngine;
import com.dongqh.lotteryservice.enums.draworder.DrawOrderStatus;
import com.dongqh.lotteryservice.mapper.draworder.DrawOrderMapper;
import com.dongqh.lotteryservice.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.enums.drawtransaction.DrawResultType;
import com.dongqh.lotteryservice.mapper.drawtransaction.ActivityPrizeInventoryMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lotteryservice.domain.model.drawtransaction.DrawExecutionContext;
import com.dongqh.lotteryservice.domain.model.drawtransaction.DrawExecutionResult;
import com.dongqh.lotteryservice.domain.model.drawtransaction.DrawPrizeSnapshot;
import com.dongqh.lotteryservice.domain.model.drawtransaction.DrawResultItem;
import com.dongqh.lotteryservice.domain.model.drawtransaction.RewardSnapshot;
import com.dongqh.lotteryservice.service.drawtransaction.DrawTransactionService;
import com.dongqh.lotteryservice.exception.LotteryDomainException;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import com.dongqh.lotteryservice.outbox.event.DrawConfirmedEvent;
import com.dongqh.lotteryservice.outbox.event.DrawEventEnvelope;
import com.dongqh.lotteryservice.outbox.event.DrawEventType;
import com.dongqh.lotteryservice.outbox.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.lotteryservice.outbox.service.OutboxService;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DrawTransactionServiceTests {

    private WeightedDrawEngine engine;
    private ActivityPrizeInventoryMapper inventory;
    private DrawOrderMapper orders;
    private DrawRecordMapper records;
    private UserBenefitMapper benefits;
    private OutboxService outbox;
    private ObjectMapper json;
    private DrawTransactionService service;
    private final AtomicLong generatedIds = new AtomicLong(100L);

    @BeforeEach
    void setUp() {
        engine = mock(WeightedDrawEngine.class);
        inventory = mock(ActivityPrizeInventoryMapper.class);
        orders = mock(DrawOrderMapper.class);
        records = mock(DrawRecordMapper.class);
        benefits = mock(UserBenefitMapper.class);
        outbox = mock(OutboxService.class);
        json = JsonMapper.builder().findAndAddModules().build();
        service = new DrawTransactionService(engine, inventory, orders, records, benefits, outbox, json);
        doAnswer(invocation -> {
            DrawRecordRow row = invocation.getArgument(0);
            row.setId(generatedIds.incrementAndGet());
            return 1;
        }).when(records).insert(any(DrawRecordRow.class));
        doAnswer(invocation -> {
            UserBenefitRow row = invocation.getArgument(0);
            row.setId(generatedIds.incrementAndGet());
            return 1;
        }).when(benefits).insert(any(UserBenefitRow.class));
    }

    @Test
    void winningDrawCopiesSnapshotsPersistsBenefitMarksOrderAndEmitsContractEvents() {
        RewardSnapshot reward = reward();
        DrawExecutionContext context = context(1, 5, prize(reward));
        when(engine.select(anyList(), anyInt())).thenReturn(DrawCandidate.prize(51L, 61L));
        when(inventory.decrementIfAvailable(51L)).thenReturn(1);
        when(orders.markSuccessIfProcessing(5L, context.drawTime())).thenReturn(1);

        DrawExecutionResult result = service.execute(context);

        assertThat(result.orderId()).isEqualTo(5L);
        assertThat(result.requestId()).isEqualTo("draw-tx-5");
        assertThat(result.status()).isEqualTo(DrawOrderStatus.SUCCESS);
        assertThat(result.completedAt()).isEqualTo(context.drawTime());
        assertThat(result.items()).containsExactly(new DrawResultItem(101L, 1, DrawResultType.WIN,
                61L, "Task Prize", PrizeType.COUPON, "https://img/prize.png", 102L, reward));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PrizeWeightSnapshot>> weights = ArgumentCaptor.forClass(List.class);
        verify(engine).select(weights.capture(), org.mockito.ArgumentMatchers.eq(3));
        assertThat(weights.getValue()).containsExactly(new PrizeWeightSnapshot(51L, 61L, 20L, 4, true));

        ArgumentCaptor<DrawRecordRow> record = ArgumentCaptor.forClass(DrawRecordRow.class);
        verify(records).insert(record.capture());
        assertRecordCopiesReward(record.getValue(), reward);
        assertThat(record.getValue().getOrderId()).isEqualTo(5L);
        assertThat(record.getValue().getRequestId()).isEqualTo(context.requestId());
        assertThat(record.getValue().getUserId()).isEqualTo(context.userId());
        assertThat(record.getValue().getActivityId()).isEqualTo(context.activityId());
        assertThat(record.getValue().getSequenceNo()).isOne();
        assertThat(record.getValue().getResultType()).isEqualTo(DrawResultType.WIN);
        assertThat(record.getValue().getDrawTime()).isEqualTo(context.drawTime());

        ArgumentCaptor<UserBenefitRow> benefit = ArgumentCaptor.forClass(UserBenefitRow.class);
        verify(benefits).insert(benefit.capture());
        assertThat(benefit.getValue().getDrawRecordId()).isEqualTo(101L);
        assertThat(benefit.getValue().getUserId()).isEqualTo(7L);
        assertThat(benefit.getValue().getPrizeId()).isEqualTo(61L);
        assertThat(benefit.getValue().getPrizeType()).isEqualTo(PrizeType.COUPON);
        assertThat(benefit.getValue().getQuantity()).isOne();
        assertThat(benefit.getValue().getStatus()).isEqualTo(BenefitStatus.PENDING);
        assertThat(benefit.getValue().getObtainedAt()).isEqualTo(context.drawTime());
        assertThat(benefit.getValue().getDrawRecordId()).isEqualTo(record.getValue().getId());
        assertBenefitCopiesReward(benefit.getValue(), reward);

        verify(inventory).decrementIfAvailable(51L);
        verify(orders).markSuccessIfProcessing(5L, context.drawTime());
        assertEvents(context, result.items());
    }

    @Test
    void noWinPersistsOnlyRecordAndConfirmationEvent() {
        DrawExecutionContext context = context(1, 6, prize(null));
        when(engine.select(anyList(), anyInt())).thenReturn(DrawCandidate.noWin());
        when(orders.markSuccessIfProcessing(6L, context.drawTime())).thenReturn(1);

        DrawExecutionResult result = service.execute(context);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.resultType()).isEqualTo(DrawResultType.NO_WIN);
            assertThat(item.prizeId()).isNull();
            assertThat(item.benefitId()).isNull();
            assertThat(item.rewardSnapshot()).isNull();
        });
        ArgumentCaptor<DrawRecordRow> inserted = ArgumentCaptor.forClass(DrawRecordRow.class);
        verify(records).insert(inserted.capture());
        assertThat(inserted.getValue().getResultType()).isEqualTo(DrawResultType.NO_WIN);
        assertThat(inserted.getValue().getPrizeId()).isNull();
        assertThat(inserted.getValue().getRewardDefinitionId()).isNull();
        verify(inventory, never()).decrementIfAvailable(org.mockito.ArgumentMatchers.anyLong());
        verify(benefits, never()).insert(any(UserBenefitRow.class));
        ArgumentCaptor<DrawEventEnvelope> event = ArgumentCaptor.forClass(DrawEventEnvelope.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(DrawEventType.DRAW_CONFIRMED);
        assertThat(event.getValue().payloadAs(DrawConfirmedEvent.class, json))
                .isEqualTo(new DrawConfirmedEvent(1, context.drawDate()));
    }

    @Test
    void stockRaceTurnsCandidateIntoNoWinWithoutRerollingTenOutcomes() {
        DrawExecutionContext context = context(10, 7, prize(null));
        when(engine.select(anyList(), anyInt())).thenReturn(DrawCandidate.prize(51L, 61L));
        when(inventory.decrementIfAvailable(51L)).thenReturn(1, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        when(orders.markSuccessIfProcessing(7L, context.drawTime())).thenReturn(1);

        DrawExecutionResult result = service.execute(context);

        assertThat(result.items()).hasSize(10);
        assertThat(result.items()).extracting(DrawResultItem::sequenceNo).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(result.items()).extracting(DrawResultItem::resultType)
                .containsExactly(DrawResultType.WIN, DrawResultType.NO_WIN, DrawResultType.NO_WIN,
                        DrawResultType.NO_WIN, DrawResultType.NO_WIN, DrawResultType.NO_WIN,
                        DrawResultType.NO_WIN, DrawResultType.NO_WIN, DrawResultType.NO_WIN,
                        DrawResultType.NO_WIN);
        verify(engine, times(10)).select(anyList(), anyInt());
        verify(inventory, times(10)).decrementIfAvailable(51L);
        verify(records, times(10)).insert(any(DrawRecordRow.class));
        verify(benefits).insert(any(UserBenefitRow.class));
    }

    @Test
    void unmatchedWinningCandidateFailsBeforeAnyBusinessWrite() {
        DrawExecutionContext context = context(1, 8, prize(null));
        when(engine.select(anyList(), anyInt())).thenReturn(DrawCandidate.prize(999L, 998L));

        assertThatThrownBy(() -> service.execute(context))
                .isInstanceOfSatisfying(LotteryDomainException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.DRAW_TRANSACTION_FAILED));
        verify(inventory, never()).decrementIfAvailable(org.mockito.ArgumentMatchers.anyLong());
        verify(records, never()).insert(any(DrawRecordRow.class));
        verify(benefits, never()).insert(any(UserBenefitRow.class));
        verify(orders, never()).markSuccessIfProcessing(org.mockito.ArgumentMatchers.anyLong(), any(LocalDateTime.class));
        verify(outbox, never()).append(any(DrawEventEnvelope.class));
    }

    @Test
    void lostProcessingStatusTransitionRejectsCompletionAndEmitsNoEvents() {
        DrawExecutionContext context = context(1, 9, prize(null));
        when(engine.select(anyList(), anyInt())).thenReturn(DrawCandidate.noWin());
        when(orders.markSuccessIfProcessing(9L, context.drawTime())).thenReturn(0);

        assertThatThrownBy(() -> service.execute(context))
                .isInstanceOfSatisfying(LotteryDomainException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.DRAW_TRANSACTION_FAILED));
        verify(orders).markSuccessIfProcessing(9L, context.drawTime());
        verify(outbox, never()).append(any(DrawEventEnvelope.class));
    }

    @Test
    void executionUsesRequiredTransactionAndReturnsImmutableOrderedResult() throws Exception {
        Transactional transactional = DrawTransactionService.class.getMethod("execute", DrawExecutionContext.class)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(OutboxService.class.getMethod("append", DrawEventEnvelope.class)
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(LotteryErrorCode.DRAW_TRANSACTION_FAILED.code()).isEqualTo(53004);
        assertThat(LotteryErrorCode.DRAW_TRANSACTION_FAILED.message()).isEqualTo("抽奖事务处理失败");
        assertThat(LotteryErrorCode.DRAW_TRANSACTION_FAILED.httpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);

        DrawExecutionResult result = new DrawExecutionResult(1L, "request", DrawOrderStatus.SUCCESS,
                LocalDateTime.of(2026, 9, 28, 10, 0), List.of());
        assertThatThrownBy(() -> result.items().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void runtimeFailureAfterEarlierWritesRollsBackRequiredTransaction() {
        DrawExecutionContext context = context(10, 10, prize(reward()));
        when(engine.select(anyList(), anyInt()))
                .thenReturn(DrawCandidate.prize(51L, 61L))
                .thenThrow(new IllegalStateException("second outcome failed"));
        when(inventory.decrementIfAvailable(51L)).thenReturn(1);

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(service);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager,
                new AnnotationTransactionAttributeSource()));
        DrawTransactionService transactionalService = (DrawTransactionService) proxyFactory.getProxy();

        assertThatThrownBy(() -> transactionalService.execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("second outcome failed");

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
        verify(records).insert(any(DrawRecordRow.class));
        verify(benefits).insert(any(UserBenefitRow.class));
        verify(orders, never()).markSuccessIfProcessing(org.mockito.ArgumentMatchers.anyLong(),
                any(LocalDateTime.class));
        verify(outbox, never()).append(any(DrawEventEnvelope.class));
    }

    private void assertEvents(DrawExecutionContext context, List<DrawResultItem> results) {
        ArgumentCaptor<DrawEventEnvelope> captured = ArgumentCaptor.forClass(DrawEventEnvelope.class);
        verify(outbox, times(2)).append(captured.capture());
        List<DrawEventEnvelope> events = captured.getAllValues();
        assertThat(events).extracting(DrawEventEnvelope::eventType)
                .containsExactly(DrawEventType.DRAW_CONFIRMED, DrawEventType.PRIZE_FULFILLMENT_REQUESTED);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.requestId()).isEqualTo(context.requestId());
            assertThat(event.userId()).isEqualTo(context.userId());
            assertThat(event.activityId()).isEqualTo(context.activityId());
            assertThat(event.orderId()).isEqualTo(context.orderId());
            assertThat(event.occurredAt()).isEqualTo(context.drawTime());
        });
        assertThat(events.get(0).payloadAs(DrawConfirmedEvent.class, json))
                .isEqualTo(new DrawConfirmedEvent(context.drawCount(), context.drawDate()));
        assertThat(events.get(1).payloadAs(PrizeFulfillmentRequestedEvent.class, json))
                .isEqualTo(new PrizeFulfillmentRequestedEvent(102L, results.get(0).recordId(), 61L,
                        PrizeType.COUPON, 71L, RewardType.COUPON, "a".repeat(64)));
    }

    private void assertRecordCopiesReward(DrawRecordRow row, RewardSnapshot reward) {
        assertThat(row.getRewardDefinitionId()).isEqualTo(reward.rewardDefinitionId());
        assertThat(row.getRewardType()).isEqualTo(reward.rewardType());
        assertThat(row.getRewardTargetId()).isEqualTo(reward.targetId());
        assertThat(row.getRewardQuantity()).isEqualTo(reward.quantity());
        assertThat(row.getRewardPayload()).isEqualTo(reward.payloadJson());
        assertThat(row.getRewardFingerprint()).isEqualTo(reward.fingerprint());
    }

    private void assertBenefitCopiesReward(UserBenefitRow row, RewardSnapshot reward) {
        assertThat(row.getRewardDefinitionId()).isEqualTo(reward.rewardDefinitionId());
        assertThat(row.getRewardType()).isEqualTo(reward.rewardType());
        assertThat(row.getRewardTargetId()).isEqualTo(reward.targetId());
        assertThat(row.getRewardQuantity()).isEqualTo(reward.quantity());
        assertThat(row.getRewardPayload()).isEqualTo(reward.payloadJson());
        assertThat(row.getRewardFingerprint()).isEqualTo(reward.fingerprint());
    }

    private DrawExecutionContext context(int drawCount, long orderId, DrawPrizeSnapshot prize) {
        return new DrawExecutionContext(orderId, "draw-tx-" + orderId, 7L, 11L, drawCount,
                LocalDate.of(2026, 9, 28), 3, List.of(prize),
                LocalDateTime.of(2026, 9, 28, 16, 10, 30));
    }

    private DrawPrizeSnapshot prize(RewardSnapshot reward) {
        return new DrawPrizeSnapshot(51L, 61L, "Task Prize", PrizeType.COUPON,
                "https://img/prize.png", 20L, 4, true, reward);
    }

    private RewardSnapshot reward() {
        return new RewardSnapshot(71L, "WELCOME-2", RewardType.COUPON, 72L, 2L,
                "{\"templateId\":72,\"quantity\":2}", "a".repeat(64));
    }
}
