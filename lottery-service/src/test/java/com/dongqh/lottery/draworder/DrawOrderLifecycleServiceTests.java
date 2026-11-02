package com.dongqh.lottery.draworder;

import com.dongqh.lottery.domain.po.draworder.DrawOrderRow;
import com.dongqh.lottery.enums.draworder.DrawOrderStatus;
import com.dongqh.lottery.mapper.draworder.DrawOrderMapper;
import com.dongqh.lottery.domain.model.draworder.NewDrawOrder;
import com.dongqh.lottery.service.draworder.DrawOrderLifecycleService;
import com.dongqh.lottery.exception.LotteryDomainException;
import com.dongqh.lottery.enums.LotteryErrorCode;
import com.dongqh.lottery.outbox.event.DrawEventEnvelope;
import com.dongqh.lottery.outbox.event.DrawEventType;
import com.dongqh.lottery.outbox.event.DrawReleaseRequestedEvent;
import com.dongqh.lottery.outbox.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DrawOrderLifecycleServiceTests {

    private DrawOrderMapper mapper;
    private OutboxService outbox;
    private ObjectMapper json;
    private DrawOrderLifecycleService service;

    @BeforeEach
    void setUp() {
        mapper = mock(DrawOrderMapper.class);
        outbox = mock(OutboxService.class);
        json = JsonMapper.builder().findAndAddModules().build();
        service = new DrawOrderLifecycleService(mapper, outbox, json);
    }

    @Test
    void createProcessingInsertsThenUsesLockingCurrentRead() {
        NewDrawOrder command = command("request-1", 21L, 31L, 10, LocalDate.of(2026, 9, 28));
        DrawOrderRow persisted = processingOrder(command);
        when(mapper.selectByRequestId(command.requestId())).thenReturn(null);
        when(mapper.selectByRequestIdForUpdate(command.requestId())).thenReturn(persisted);

        DrawOrderRow result = service.createProcessing(command);

        assertThat(result).isSameAs(persisted);
        ArgumentCaptor<DrawOrderRow> inserted = ArgumentCaptor.forClass(DrawOrderRow.class);
        verify(mapper).insertProcessingIfAbsent(inserted.capture());
        assertThat(inserted.getValue().getStatus()).isEqualTo(DrawOrderStatus.PROCESSING);
        assertThat(inserted.getValue().getDrawDate()).isEqualTo(command.drawDate());
        var sequence = inOrder(mapper);
        sequence.verify(mapper).selectByRequestId(command.requestId());
        sequence.verify(mapper).insertProcessingIfAbsent(org.mockito.ArgumentMatchers.any());
        sequence.verify(mapper).selectByRequestIdForUpdate(command.requestId());
    }

    @Test
    void createProcessingReturnsExistingIdentityWhenRetryDrawDateDiffers() {
        NewDrawOrder command = command("request-2", 21L, 31L, 1, LocalDate.of(2026, 9, 28));
        DrawOrderRow existing = processingOrder(command);
        when(mapper.selectByRequestId(command.requestId())).thenReturn(existing);

        DrawOrderRow result = service.createProcessing(command("request-2", 21L, 31L, 1,
                LocalDate.of(2026, 9, 29)));

        assertThat(result).isSameAs(existing);
        assertThat(result.getDrawDate()).isEqualTo(LocalDate.of(2026, 9, 28));
        verify(mapper, never()).insertProcessingIfAbsent(org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).selectByRequestIdForUpdate(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void createProcessingRejectsChangedIdentityWithLotteryIdempotencyContract() {
        NewDrawOrder original = command("request-3", 21L, 31L, 1, LocalDate.of(2026, 9, 28));
        when(mapper.selectByRequestId("request-3")).thenReturn(processingOrder(original));

        assertConflict(command("request-3", 22L, 31L, 1, original.drawDate()));
        assertConflict(command("request-3", 21L, 32L, 1, original.drawDate()));
        assertConflict(command("request-3", 21L, 31L, 10, original.drawDate()));
        verify(mapper, never()).insertProcessingIfAbsent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createProcessingRejectsMissingLockingReadAsIdempotencyConflict() {
        NewDrawOrder command = command("request-race", 21L, 31L, 1, LocalDate.of(2026, 9, 28));
        when(mapper.selectByRequestId(command.requestId())).thenReturn(null);
        when(mapper.selectByRequestIdForUpdate(command.requestId())).thenReturn(null);

        assertConflict(command);
        verify(mapper).insertProcessingIfAbsent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void markFailedTrimsReasonAndUsesConditionalMapperTransition() {
        when(mapper.markFailedIfProcessing(41L, "safe failure")).thenReturn(1);

        service.markFailed(41L, "  safe failure  ");

        verify(mapper).markFailedIfProcessing(41L, "safe failure");
    }

    @Test
    void markFailedUsesFallbackAndBoundsSafeReasonToFiveHundredCharacters() {
        service.markFailed(41L, "  ");
        service.markFailed(42L, null);
        service.markFailed(43L, "x".repeat(700));

        verify(mapper).markFailedIfProcessing(41L, "DRAW_TRANSACTION_FAILED");
        verify(mapper).markFailedIfProcessing(42L, "DRAW_TRANSACTION_FAILED");
        verify(mapper).markFailedIfProcessing(43L, "x".repeat(500));
    }

    @Test
    void markFailedRejectsNonPositiveOrderIds() {
        assertThatThrownBy(() -> service.markFailed(0, "safe"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.markFailed(-1, "safe"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mapper, never()).markFailedIfProcessing(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void failureAndReleaseAppendOnlyWhenProcessingTransitionWins() {
        DrawOrderRow order = processingOrder(command("request-release", 21L, 31L, 10,
                LocalDate.of(2026, 9, 28)));
        order.setId(41L);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 28, 16, 10, 30);
        when(mapper.markFailedIfProcessing(41L, "safe failure")).thenReturn(1);

        service.markFailedAndRequestRelease(order, " safe failure ", occurredAt);

        ArgumentCaptor<DrawEventEnvelope> event = ArgumentCaptor.forClass(DrawEventEnvelope.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(DrawEventType.DRAW_RELEASE_REQUESTED);
        assertThat(event.getValue().requestId()).isEqualTo("request-release");
        assertThat(event.getValue().userId()).isEqualTo(21L);
        assertThat(event.getValue().activityId()).isEqualTo(31L);
        assertThat(event.getValue().orderId()).isEqualTo(41L);
        assertThat(event.getValue().occurredAt()).isEqualTo(occurredAt);
        assertThat(event.getValue().payloadAs(DrawReleaseRequestedEvent.class, json))
                .isEqualTo(new DrawReleaseRequestedEvent(10, order.getDrawDate(), "safe failure"));
        verify(mapper).markFailedIfProcessing(41L, "safe failure");
    }

    @Test
    void failureAndReleaseDoNotAppendWhenOrderIsNoLongerProcessing() {
        DrawOrderRow order = processingOrder(command("request-lost", 21L, 31L, 1,
                LocalDate.of(2026, 9, 28)));
        order.setId(42L);
        when(mapper.markFailedIfProcessing(42L, "DRAW_TRANSACTION_FAILED")).thenReturn(0);

        service.markFailedAndRequestRelease(order, null, LocalDateTime.of(2026, 9, 28, 16, 0));

        verify(outbox, never()).append(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void lifecycleTransactionsAndConflictErrorRemainCompatible() throws Exception {
        assertRequiresNew("createProcessing", NewDrawOrder.class);
        assertRequiresNew("markFailed", long.class, String.class);
        assertRequiresNew("markFailedAndRequestRelease", DrawOrderRow.class, String.class, LocalDateTime.class);
        assertThat(LotteryErrorCode.IDEMPOTENCY_CONFLICT.code()).isEqualTo(43004);
        assertThat(LotteryErrorCode.IDEMPOTENCY_CONFLICT.message()).isEqualTo("重复请求的参数不一致");
        assertThat(LotteryErrorCode.IDEMPOTENCY_CONFLICT.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void newDrawOrderValidatesRequestIdentityAndDrawParameters() {
        LocalDate date = LocalDate.of(2026, 9, 28);
        assertThatThrownBy(() -> command(" ", 1L, 1L, 1, date)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("x".repeat(65), 1L, 1L, 1, date))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("valid", 0L, 1L, 1, date)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("valid", 1L, -1L, 1, date)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("valid", 1L, 1L, 2, date)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("valid", 1L, 1L, 1, null)).isInstanceOf(NullPointerException.class);
        assertThat(command("x".repeat(64), 1L, 1L, 1, date).requestId()).hasSize(64);
    }

    private void assertConflict(NewDrawOrder command) {
        assertThatThrownBy(() -> service.createProcessing(command))
                .isInstanceOfSatisfying(LotteryDomainException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(LotteryErrorCode.IDEMPOTENCY_CONFLICT));
    }

    private void assertRequiresNew(String methodName, Class<?>... parameterTypes) throws Exception {
        Transactional transactional = DrawOrderLifecycleService.class.getMethod(methodName, parameterTypes)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private NewDrawOrder command(String requestId, long userId, long activityId, int drawCount, LocalDate drawDate) {
        return new NewDrawOrder(requestId, userId, activityId, drawCount, drawDate);
    }

    private DrawOrderRow processingOrder(NewDrawOrder command) {
        DrawOrderRow row = new DrawOrderRow();
        row.setId(41L);
        row.setRequestId(command.requestId());
        row.setUserId(command.userId());
        row.setActivityId(command.activityId());
        row.setDrawCount(command.drawCount());
        row.setDrawDate(command.drawDate());
        row.setStatus(DrawOrderStatus.PROCESSING);
        return row;
    }
}
