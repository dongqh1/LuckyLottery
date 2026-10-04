package com.dongqh.lotteryservice.drawchance;

import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import com.dongqh.lotteryservice.controller.DrawChanceController;
import com.dongqh.lotteryservice.drawchance.entity.DrawChanceAccountRow;
import com.dongqh.lotteryservice.drawchance.entity.DrawChanceLedgerRow;
import com.dongqh.lotteryservice.drawchance.entity.DrawChanceReservationRow;
import com.dongqh.lotteryservice.drawchance.enums.DrawChanceBusinessType;
import com.dongqh.lotteryservice.drawchance.enums.DrawChanceDirection;
import com.dongqh.lotteryservice.drawchance.enums.DrawChanceReservationStatus;
import com.dongqh.lotteryservice.drawchance.mapper.DrawChanceAccountMapper;
import com.dongqh.lotteryservice.drawchance.mapper.DrawChanceLedgerMapper;
import com.dongqh.lotteryservice.drawchance.mapper.DrawChanceReservationMapper;
import com.dongqh.lotteryservice.drawchance.model.DrawChanceReservationCommand;
import com.dongqh.lotteryservice.drawchance.service.DrawChanceReservationService;
import com.dongqh.lotteryservice.draworder.entity.DrawOrderRow;
import com.dongqh.lotteryservice.draworder.enums.DrawOrderStatus;
import com.dongqh.lotteryservice.draworder.mapper.DrawOrderMapper;
import com.dongqh.lotteryservice.domain.LotteryDomainException;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DrawChanceReservationServiceTests {
    private DrawChanceAccountMapper accounts;
    private DrawChanceLedgerMapper ledgers;
    private DrawChanceReservationMapper reservations;
    private DrawOrderMapper orders;
    private DrawChanceReservationService service;

    @BeforeEach
    void setUp() {
        accounts = mock(DrawChanceAccountMapper.class);
        ledgers = mock(DrawChanceLedgerMapper.class);
        reservations = mock(DrawChanceReservationMapper.class);
        orders = mock(DrawOrderMapper.class);
        service = new DrawChanceReservationService(accounts, ledgers, reservations, orders);
    }

    @Test
    void reserveUsesRequestIdentityLockBeforeAccountMovementAndReturnsActiveAggregate() {
        DrawChanceReservationCommand command = command("request-1", 21L, 31L, 10,
                LocalDate.of(2026, 9, 28));
        DrawChanceReservationRow reservation = reservation(command, 501L, 0L,
                DrawChanceReservationStatus.RESERVED);
        DrawChanceAccountRow account = account(601L, command.userId(), 3L, 1L);
        when(reservations.insertIfAbsent(any())).thenReturn(1);
        when(reservations.lockByRequestId(command.requestId())).thenReturn(reservation);
        when(accounts.lockByUserId(command.userId())).thenReturn(account);
        when(accounts.updateBalances(601L, 0L, 4L)).thenReturn(1);
        when(reservations.setBonus(501L, 3L)).thenReturn(1);
        when(reservations.sumActiveBonus(command.userId(), command.activityId(), command.drawDate())).thenReturn(5L);

        var result = service.reserve(command);

        assertThat(result).isEqualTo(new com.dongqh.lotteryservice.drawchance.model.DrawChanceReservationResult(
                command.requestId(), command.activityId(), command.userId(), command.drawCount(),
                command.drawDate(), 3L, 5L, DrawChanceReservationStatus.RESERVED, false));
        ArgumentCaptor<DrawChanceReservationRow> inserted = ArgumentCaptor.forClass(DrawChanceReservationRow.class);
        verify(reservations).insertIfAbsent(inserted.capture());
        assertThat(inserted.getValue().getRequestId()).isEqualTo(command.requestId());
        assertThat(inserted.getValue().getUserId()).isEqualTo(command.userId());
        assertThat(inserted.getValue().getActivityId()).isEqualTo(command.activityId());
        assertThat(inserted.getValue().getDrawCount()).isEqualTo(command.drawCount());
        assertThat(inserted.getValue().getDrawDate()).isEqualTo(command.drawDate());
        var order = inOrder(reservations, accounts);
        order.verify(reservations).insertIfAbsent(any());
        order.verify(reservations).lockByRequestId(command.requestId());
        order.verify(accounts).insertIfAbsent(command.userId());
        order.verify(accounts).lockByUserId(command.userId());
        order.verify(accounts).updateBalances(601L, 0L, 4L);
        order.verify(reservations).setBonus(501L, 3L);
        verify(ledgers, never()).insert(any(DrawChanceLedgerRow.class));
    }

    @Test
    void duplicateReservationIsIdentityCheckedAndDoesNotMoveAccountAgain() {
        DrawChanceReservationCommand command = command("request-2", 21L, 31L, 1,
                LocalDate.of(2026, 9, 28));
        when(reservations.insertIfAbsent(any())).thenReturn(0);
        when(reservations.lockByRequestId(command.requestId())).thenReturn(reservation(command, 502L, 1L,
                DrawChanceReservationStatus.RESERVED));
        when(reservations.sumActiveBonus(command.userId(), command.activityId(), command.drawDate())).thenReturn(1L);

        var duplicate = service.reserve(command);

        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.bonusReserved()).isEqualTo(1L);
        verify(accounts, never()).insertIfAbsent(command.userId());
        verify(accounts, never()).lockByUserId(command.userId());
        verify(accounts, never()).updateBalances(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void reusedRequestIdWithDifferentIdentityFailsBeforeAccountAccess() {
        DrawChanceReservationCommand command = command("request-3", 21L, 31L, 1,
                LocalDate.of(2026, 9, 28));
        when(reservations.insertIfAbsent(any())).thenReturn(0);
        when(reservations.lockByRequestId(command.requestId())).thenReturn(reservation(command, 503L, 0L,
                DrawChanceReservationStatus.RESERVED));

        List<DrawChanceReservationCommand> conflicts = List.of(
                command("request-3", 22L, 31L, 1, command.drawDate()),
                command("request-3", 21L, 32L, 1, command.drawDate()),
                command("request-3", 21L, 31L, 10, command.drawDate()),
                command("request-3", 21L, 31L, 1, command.drawDate().plusDays(1)));
        for (DrawChanceReservationCommand conflicting : conflicts) {
            assertThatThrownBy(() -> service.reserve(conflicting))
                    .isInstanceOfSatisfying(LotteryDomainException.class, error ->
                            assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.DRAW_CHANCE_IDENTITY_CONFLICT));
        }
        verify(accounts, never()).insertIfAbsent(org.mockito.ArgumentMatchers.anyLong());
        verify(reservations, never()).sumActiveBonus(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void failureAfterAccountMovementRollsBackTheReservationTransaction() {
        DrawChanceReservationCommand command = command("request-rollback", 21L, 31L, 1,
                LocalDate.of(2026, 9, 28));
        DrawChanceReservationRow reservation = reservation(command, 510L, 0L,
                DrawChanceReservationStatus.RESERVED);
        when(reservations.insertIfAbsent(any())).thenReturn(1);
        when(reservations.lockByRequestId(command.requestId())).thenReturn(reservation);
        when(accounts.lockByUserId(command.userId())).thenReturn(account(610L, command.userId(), 2L, 0L));
        when(accounts.updateBalances(610L, 1L, 1L)).thenReturn(1);
        when(reservations.setBonus(510L, 1L)).thenReturn(0);

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(service);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager,
                new AnnotationTransactionAttributeSource()));
        DrawChanceReservationService transactionalService = (DrawChanceReservationService) proxyFactory.getProxy();

        assertThatThrownBy(() -> transactionalService.reserve(command))
                .isInstanceOfSatisfying(LotteryDomainException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.DRAW_CHANCE_BALANCE_INVALID));

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
        verify(accounts).updateBalances(610L, 1L, 1L);
    }

    @Test
    void confirmWritesDebitLedgerAndTransitionsOnceWhileRepeatIsIdempotent() {
        DrawChanceReservationCommand command = command("request-4", 21L, 31L, 10,
                LocalDate.of(2026, 9, 28));
        DrawChanceReservationRow reservation = reservation(command, 504L, 2L,
                DrawChanceReservationStatus.RESERVED);
        DrawChanceAccountRow account = account(604L, command.userId(), 7L, 5L);
        when(reservations.lockByRequestId(command.requestId())).thenReturn(reservation);
        when(accounts.lockByUserId(command.userId())).thenReturn(account);
        when(accounts.updateBalances(604L, 7L, 3L)).thenReturn(1);
        when(reservations.settle(504L, DrawChanceReservationStatus.CONFIRMED)).thenReturn(1);

        service.confirm(command.requestId());
        service.confirm(command.requestId());

        ArgumentCaptor<DrawChanceLedgerRow> ledger = ArgumentCaptor.forClass(DrawChanceLedgerRow.class);
        verify(ledgers).insert(ledger.capture());
        assertThat(ledger.getValue().getUserId()).isEqualTo(command.userId());
        assertThat(ledger.getValue().getBusinessType()).isEqualTo(DrawChanceBusinessType.DRAW_CONSUME);
        assertThat(ledger.getValue().getBusinessId()).isEqualTo(command.requestId());
        assertThat(ledger.getValue().getDirection()).isEqualTo(DrawChanceDirection.DEBIT);
        assertThat(ledger.getValue().getAmount()).isEqualTo(2L);
        assertThat(ledger.getValue().getAvailableAfter()).isEqualTo(7L);
        assertThat(ledger.getValue().getReservedAfter()).isEqualTo(3L);
        verify(accounts).updateBalances(604L, 7L, 3L);
        verify(reservations).settle(504L, DrawChanceReservationStatus.CONFIRMED);
    }

    @Test
    void releaseCreditsBackAndRejectsConflictingTerminalTransition() {
        DrawChanceReservationCommand command = command("request-5", 21L, 31L, 1,
                LocalDate.of(2026, 9, 28));
        DrawChanceReservationRow reservation = reservation(command, 505L, 2L,
                DrawChanceReservationStatus.RESERVED);
        DrawChanceAccountRow account = account(605L, command.userId(), 3L, 2L);
        when(reservations.lockByRequestId(command.requestId())).thenReturn(reservation);
        when(accounts.lockByUserId(command.userId())).thenReturn(account);
        when(accounts.updateBalances(605L, 5L, 0L)).thenReturn(1);
        when(reservations.settle(505L, DrawChanceReservationStatus.RELEASED)).thenReturn(1);

        service.release(command.requestId());
        service.release(command.requestId());
        assertThatThrownBy(() -> service.confirm(command.requestId()))
                .isInstanceOfSatisfying(LotteryDomainException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.DRAW_CHANCE_TERMINAL_STATE_CONFLICT));

        ArgumentCaptor<DrawChanceLedgerRow> ledger = ArgumentCaptor.forClass(DrawChanceLedgerRow.class);
        verify(ledgers).insert(ledger.capture());
        assertThat(ledger.getValue().getBusinessType()).isEqualTo(DrawChanceBusinessType.DRAW_RELEASE);
        assertThat(ledger.getValue().getDirection()).isEqualTo(DrawChanceDirection.CREDIT);
        assertThat(ledger.getValue().getAmount()).isEqualTo(2L);
        assertThat(ledger.getValue().getAvailableAfter()).isEqualTo(5L);
        assertThat(ledger.getValue().getReservedAfter()).isZero();
        verify(reservations).settle(505L, DrawChanceReservationStatus.RELEASED);
    }

    @Test
    void releaseRejectsOverflowAndInsufficientReservedBalanceBeforeAnyMutation() {
        DrawChanceReservationCommand command = command("request-6", 21L, 31L, 1,
                LocalDate.of(2026, 9, 28));
        DrawChanceReservationRow overflowReservation = reservation(command, 506L, 1L,
                DrawChanceReservationStatus.RESERVED);
        when(reservations.lockByRequestId(command.requestId())).thenReturn(overflowReservation);
        when(accounts.lockByUserId(command.userId())).thenReturn(account(606L, command.userId(), Long.MAX_VALUE, 1L));

        assertThatThrownBy(() -> service.release(command.requestId()))
                .isInstanceOfSatisfying(LotteryDomainException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.DRAW_CHANCE_BALANCE_INVALID));
        verify(accounts, never()).updateBalances(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        verify(ledgers, never()).insert(any(DrawChanceLedgerRow.class));
        verify(reservations, never()).settle(org.mockito.ArgumentMatchers.anyLong(), any());

        DrawChanceReservationRow underfunded = reservation(command, 506L, 2L,
                DrawChanceReservationStatus.RESERVED);
        when(reservations.lockByRequestId(command.requestId())).thenReturn(underfunded);
        when(accounts.lockByUserId(command.userId())).thenReturn(account(606L, command.userId(), 0L, 1L));
        assertThatThrownBy(() -> service.confirm(command.requestId()))
                .isInstanceOfSatisfying(LotteryDomainException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.DRAW_CHANCE_BALANCE_INVALID));
        verify(accounts, never()).updateBalances(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        verify(ledgers, never()).insert(any(DrawChanceLedgerRow.class));
    }

    @Test
    void reconciliationSettlesOnlyActualTerminalOrMissingOrdersAndLeavesProcessingAlone() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 28, 10, 0);
        DrawChanceReservationRow success = reservation(command("reconcile-success", 21L, 31L, 1,
                LocalDate.of(2026, 9, 28)), 701L, 1L, DrawChanceReservationStatus.RESERVED);
        DrawChanceReservationRow failed = reservation(command("reconcile-failed", 21L, 31L, 1,
                LocalDate.of(2026, 9, 28)), 702L, 1L, DrawChanceReservationStatus.RESERVED);
        DrawChanceReservationRow missing = reservation(command("reconcile-missing", 21L, 31L, 1,
                LocalDate.of(2026, 9, 28)), 703L, 1L, DrawChanceReservationStatus.RESERVED);
        DrawChanceReservationRow processing = reservation(command("reconcile-processing", 21L, 31L, 1,
                LocalDate.of(2026, 9, 28)), 704L, 1L, DrawChanceReservationStatus.RESERVED);
        when(reservations.lockExpired(10, cutoff)).thenReturn(List.of(success, failed, missing, processing));
        when(orders.selectByRequestIdForUpdate("reconcile-success")).thenReturn(order(DrawOrderStatus.SUCCESS));
        when(orders.selectByRequestIdForUpdate("reconcile-failed")).thenReturn(order(DrawOrderStatus.FAILED));
        when(orders.selectByRequestIdForUpdate("reconcile-missing")).thenReturn(null);
        when(orders.selectByRequestIdForUpdate("reconcile-processing")).thenReturn(order(DrawOrderStatus.PROCESSING));
        when(accounts.lockByUserId(21L)).thenReturn(account(801L, 21L, 5L, 4L));
        when(accounts.updateBalances(801L, 5L, 3L)).thenReturn(1);
        when(accounts.updateBalances(801L, 6L, 2L)).thenReturn(1);
        when(accounts.updateBalances(801L, 7L, 1L)).thenReturn(1);
        when(reservations.settle(701L, DrawChanceReservationStatus.CONFIRMED)).thenReturn(1);
        when(reservations.settle(702L, DrawChanceReservationStatus.RELEASED)).thenReturn(1);
        when(reservations.settle(703L, DrawChanceReservationStatus.RELEASED)).thenReturn(1);

        int settled = service.reconcileExpired(10, cutoff);

        assertThat(settled).isEqualTo(3);
        verify(reservations).settle(701L, DrawChanceReservationStatus.CONFIRMED);
        verify(reservations).settle(702L, DrawChanceReservationStatus.RELEASED);
        verify(reservations).settle(703L, DrawChanceReservationStatus.RELEASED);
        verify(reservations, never()).settle(704L, DrawChanceReservationStatus.CONFIRMED);
        verify(reservations, never()).settle(704L, DrawChanceReservationStatus.RELEASED);
        verify(ledgers, org.mockito.Mockito.times(3)).insert(any(DrawChanceLedgerRow.class));
        verify(orders).selectByRequestIdForUpdate("reconcile-success");
        verify(orders).selectByRequestIdForUpdate("reconcile-failed");
        verify(orders).selectByRequestIdForUpdate("reconcile-missing");
        verify(orders).selectByRequestIdForUpdate("reconcile-processing");
    }

    @Test
    void reconcileRejectsInvalidBoundsAndKeepsOnlyAuthorizedReadEndpointExposed() throws Exception {
        assertThatThrownBy(() -> service.reconcileExpired(0, LocalDateTime.now()))
                .isInstanceOf(LotteryDomainException.class);
        assertThatThrownBy(() -> service.reconcileExpired(1001, LocalDateTime.now()))
                .isInstanceOf(LotteryDomainException.class);
        assertThatThrownBy(() -> service.reconcileExpired(1, null))
                .isInstanceOf(LotteryDomainException.class);
        assertThat(DrawChanceController.class.getDeclaredMethods())
                .extracting(Method::getName).containsExactly("mine");
        Method readEndpoint = DrawChanceController.class.getDeclaredMethod("mine");
        assertThat(readEndpoint.getAnnotation(GetMapping.class)).isNotNull();
        RequirePermission permission = readEndpoint.getAnnotation(RequirePermission.class);
        assertThat(permission).isNotNull();
        assertThat(permission.value()).isEqualTo(ServicePermissionCodes.LOTTERY_ACTIVITY_READ);
    }

    @Test
    void drawChanceErrorsRetainMarketingCodesMessagesAndHttpStatuses() {
        assertThat(LotteryErrorCode.DRAW_CHANCE_INVALID_REQUEST.code()).isEqualTo(46001);
        assertThat(LotteryErrorCode.DRAW_CHANCE_INVALID_REQUEST.message()).isEqualTo("抽奖次数请求不合法");
        assertThat(LotteryErrorCode.DRAW_CHANCE_INVALID_REQUEST.httpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThat(LotteryErrorCode.DRAW_CHANCE_IDENTITY_CONFLICT.code()).isEqualTo(46003);
        assertThat(LotteryErrorCode.DRAW_CHANCE_IDENTITY_CONFLICT.message()).isEqualTo("抽奖次数请求身份冲突");
        assertThat(LotteryErrorCode.DRAW_CHANCE_IDENTITY_CONFLICT.httpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(LotteryErrorCode.DRAW_CHANCE_RESERVATION_NOT_FOUND.code()).isEqualTo(46004);
        assertThat(LotteryErrorCode.DRAW_CHANCE_RESERVATION_NOT_FOUND.message()).isEqualTo("抽奖次数预留不存在");
        assertThat(LotteryErrorCode.DRAW_CHANCE_RESERVATION_NOT_FOUND.httpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(LotteryErrorCode.DRAW_CHANCE_TERMINAL_STATE_CONFLICT.code()).isEqualTo(46005);
        assertThat(LotteryErrorCode.DRAW_CHANCE_TERMINAL_STATE_CONFLICT.message()).isEqualTo("抽奖次数预留终态冲突");
        assertThat(LotteryErrorCode.DRAW_CHANCE_TERMINAL_STATE_CONFLICT.httpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(LotteryErrorCode.DRAW_CHANCE_BALANCE_INVALID.code()).isEqualTo(46006);
        assertThat(LotteryErrorCode.DRAW_CHANCE_BALANCE_INVALID.message()).isEqualTo("抽奖次数账户余额异常");
        assertThat(LotteryErrorCode.DRAW_CHANCE_BALANCE_INVALID.httpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void reservationOperationsRemainRequiredTransactionsAndOnlyLotteryOwnedMapperSqlIsUsed() throws Exception {
        assertRequiredTransaction("reserve", DrawChanceReservationCommand.class);
        assertRequiredTransaction("confirm", String.class);
        assertRequiredTransaction("release", String.class);
        assertRequiredTransaction("reconcileExpired", int.class, LocalDateTime.class);

        Insert insertAccount = DrawChanceAccountMapper.class.getMethod("insertIfAbsent", long.class)
                .getAnnotation(Insert.class);
        assertThat(insertAccount.value()[0]).contains("INSERT INTO draw_chance_account", "ON DUPLICATE KEY UPDATE");
        Select lockAccount = DrawChanceAccountMapper.class.getMethod("lockByUserId", long.class)
                .getAnnotation(Select.class);
        assertThat(lockAccount.value()[0]).contains("FROM draw_chance_account", "FOR UPDATE");
        Select readAccount = DrawChanceAccountMapper.class.getMethod("selectByUserId", long.class)
                .getAnnotation(Select.class);
        assertThat(readAccount.value()[0]).contains("FROM draw_chance_account")
                .doesNotContain("sys_user", "FOR UPDATE");
        Update updateAccount = DrawChanceAccountMapper.class.getMethod("updateBalances", long.class,
                long.class, long.class).getAnnotation(Update.class);
        assertThat(updateAccount.value()[0]).contains("available_balance=#{available}",
                "reserved_balance=#{reserved}", "version=version+1");

        Insert insertReservation = DrawChanceReservationMapper.class.getMethod("insertIfAbsent",
                DrawChanceReservationRow.class).getAnnotation(Insert.class);
        assertThat(String.join(" ", insertReservation.value())).contains("INSERT IGNORE INTO draw_chance_reservation",
                "request_id,user_id,activity_id,draw_date,draw_count,bonus_reserved,status", "'RESERVED'");
        Select lockReservation = DrawChanceReservationMapper.class.getMethod("lockByRequestId", String.class)
                .getAnnotation(Select.class);
        assertThat(lockReservation.value()[0]).contains("FROM draw_chance_reservation", "FOR UPDATE");
        Select activeBonus = DrawChanceReservationMapper.class.getMethod("sumActiveBonus", long.class,
                long.class, LocalDate.class).getAnnotation(Select.class);
        assertThat(String.join(" ", activeBonus.value())).contains("status IN ('RESERVED','CONFIRMED')");
        Select expired = DrawChanceReservationMapper.class.getMethod("lockExpired", int.class,
                LocalDateTime.class).getAnnotation(Select.class);
        assertThat(String.join(" ", expired.value())).contains("NOT EXISTS", "o.status='PROCESSING'",
                "ORDER BY r.created_at,r.id LIMIT #{limit} FOR UPDATE SKIP LOCKED");
        Update settle = DrawChanceReservationMapper.class.getMethod("settle", long.class,
                DrawChanceReservationStatus.class).getAnnotation(Update.class);
        assertThat(settle.value()[0]).contains("settled_at=CURRENT_TIMESTAMP(3)", "status='RESERVED'");
        Insert insertLedger = DrawChanceLedgerMapper.class.getMethod("insert", DrawChanceLedgerRow.class)
                .getAnnotation(Insert.class);
        assertThat(String.join(" ", insertLedger.value())).contains("INSERT INTO draw_chance_ledger",
                "#{businessType}", "#{businessId}", "#{direction}", "#{amount}");
        Select selectLedgerBusiness = DrawChanceLedgerMapper.class.getMethod("selectBusiness",
                DrawChanceBusinessType.class, String.class).getAnnotation(Select.class);
        assertThat(selectLedgerBusiness.value()[0]).contains("FROM draw_chance_ledger",
                "business_type=#{type}", "business_id=#{businessId}");
        assertThat(DrawChanceAccountMapper.class.getDeclaredMethods())
                .extracting(Method::getName).containsExactlyInAnyOrder(
                        "insertIfAbsent", "lockByUserId", "selectByUserId", "updateBalances");
        assertThat(DrawChanceLedgerMapper.class.getDeclaredMethods())
                .extracting(Method::getName).containsExactlyInAnyOrder("insert", "selectBusiness");
        assertThat(DrawChanceReservationMapper.class.getDeclaredMethods())
                .extracting(Method::getName).containsExactlyInAnyOrder(
                        "insertIfAbsent", "lockByRequestId", "setBonus", "settle", "sumActiveBonus", "lockExpired");
        assertThat(DrawChanceLedgerMapper.class.getName()).contains("lotteryservice.drawchance");
    }

    private void assertRequiredTransaction(String methodName, Class<?>... parameters) throws Exception {
        Transactional transactional = DrawChanceReservationService.class.getMethod(methodName, parameters)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }

    private DrawChanceReservationCommand command(String requestId, long userId, long activityId,
                                                 int drawCount, LocalDate date) {
        return new DrawChanceReservationCommand(requestId, activityId, userId, drawCount, date);
    }

    private DrawChanceReservationRow reservation(DrawChanceReservationCommand command, long id,
                                                 long bonus, DrawChanceReservationStatus status) {
        DrawChanceReservationRow row = new DrawChanceReservationRow();
        row.setId(id);
        row.setRequestId(command.requestId());
        row.setUserId(command.userId());
        row.setActivityId(command.activityId());
        row.setDrawCount(command.drawCount());
        row.setDrawDate(command.drawDate());
        row.setBonusReserved(bonus);
        row.setStatus(status);
        return row;
    }

    private DrawChanceAccountRow account(long id, long userId, long available, long reserved) {
        DrawChanceAccountRow row = new DrawChanceAccountRow();
        row.setId(id);
        row.setUserId(userId);
        row.setAvailableBalance(available);
        row.setReservedBalance(reserved);
        return row;
    }

    private DrawOrderRow order(DrawOrderStatus status) {
        DrawOrderRow row = new DrawOrderRow();
        row.setStatus(status);
        return row;
    }
}
