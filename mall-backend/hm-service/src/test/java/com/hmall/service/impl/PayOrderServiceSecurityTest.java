package com.hmall.service.impl;

import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.UserContext;
import com.hmall.domain.dto.PayOrderFormDTO;
import com.hmall.domain.po.Order;
import com.hmall.domain.po.PayOrder;
import com.hmall.enums.PayStatus;
import com.hmall.integration.marketing.CouponCheckoutClient;
import com.hmall.service.IOrderService;
import com.hmall.service.IUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class PayOrderServiceSecurityTest {

    @AfterEach
    void clearUser() {
        UserContext.removeUser();
    }

    @Test
    void rejectsPaymentOwnedByAnotherUser() {
        Fixture fixture = fixture(waitingPayOrder(99L, 500), waitingOrder(99L, 500));
        UserContext.setUser(42L);

        assertThatThrownBy(() -> fixture.service.tryPayOrderByBalance(form()))
                .isInstanceOf(BizIllegalException.class);
        verify(fixture.userService, never()).deductMoney(any(), any());
    }

    @Test
    void rejectsWhenBusinessOrderAmountDoesNotMatchPayment() {
        Fixture fixture = fixture(waitingPayOrder(42L, 500), waitingOrder(42L, 600));
        UserContext.setUser(42L);

        assertThatThrownBy(() -> fixture.service.tryPayOrderByBalance(form()))
                .isInstanceOf(BizIllegalException.class);
        verify(fixture.userService, never()).deductMoney(any(), any());
    }

    @Test
    void casFailureNeverDeductsBalance() {
        Fixture fixture = fixture(waitingPayOrder(42L, 500), waitingOrder(42L, 500));
        doReturn(false).when(fixture.service).markPayOrderSuccess(eq(100L), any(LocalDateTime.class));
        UserContext.setUser(42L);

        assertThatThrownBy(() -> fixture.service.tryPayOrderByBalance(form()))
                .isInstanceOf(BizIllegalException.class);
        verify(fixture.userService, never()).deductMoney(any(), any());
    }

    @Test
    void validOwnerPaysOnlyAfterBothOrdersAreValidated() {
        Fixture fixture = fixture(waitingPayOrder(42L, 500), waitingOrder(42L, 500));
        doReturn(true).when(fixture.service).markPayOrderSuccess(eq(100L), any(LocalDateTime.class));
        doReturn(true).when(fixture.service).markBusinessOrderPaid(any(Order.class), any(LocalDateTime.class));
        UserContext.setUser(42L);

        assertThatCode(() -> fixture.service.tryPayOrderByBalance(form())).doesNotThrowAnyException();
        verify(fixture.userService).deductMoney("pw", 500);
        verify(fixture.service).markBusinessOrderPaid(any(Order.class), any(LocalDateTime.class));
    }

    @Test
    void successfulPaymentMarksReservedCouponUsedForTheSameOrder() {
        Order order = waitingOrder(42L, 500).setCouponId(77L);
        Fixture fixture = fixture(waitingPayOrder(42L, 500), order);
        doReturn(true).when(fixture.service).markPayOrderSuccess(eq(100L), any(LocalDateTime.class));
        doReturn(true).when(fixture.service).markBusinessOrderPaid(any(Order.class), any(LocalDateTime.class));
        UserContext.setUser(42L);

        fixture.service.tryPayOrderByBalance(form());

        verify(fixture.couponClient).use(77L, 200L);
    }

    @Test
    void paymentReloadsAfterBusinessOrderLockSoPreCouponReadCannotChargeOldAmount() {
        Fixture fixture = fixture(waitingPayOrder(42L, 9500), waitingOrder(42L, 8500));
        com.hmall.mapper.PayOrderMapper mapper = org.mockito.Mockito.mock(com.hmall.mapper.PayOrderMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(fixture.service,"baseMapper",mapper);
        org.mockito.Mockito.when(mapper.selectOne(any())).thenReturn(waitingPayOrder(42L,8500));
        doReturn(true).when(fixture.service).markPayOrderSuccess(eq(100L),any(LocalDateTime.class));
        doReturn(true).when(fixture.service).markBusinessOrderPaid(any(Order.class),any(LocalDateTime.class));
        UserContext.setUser(42L);
        fixture.service.tryPayOrderByBalance(form());
        verify(fixture.userService).deductMoney("pw",8500);
        verify(fixture.userService,never()).deductMoney("pw",9500);
    }

    @Test
    void paymentCreationIgnoresBrowserAmountAndLocksAuthoritativeOrder() {
        IUserService users = mock(IUserService.class);
        IOrderService orders = mock(IOrderService.class);
        PayOrderServiceImpl service = spy(new PayOrderServiceImpl(users,orders,mock(CouponCheckoutClient.class)));
        org.mockito.Mockito.when(orders.lockOrder(200L)).thenReturn(waitingOrder(42L,8500));
        doReturn(null).when(service).queryByBizOrderNo(200L);
        org.mockito.Mockito.doAnswer(i -> { ((PayOrder)i.getArgument(0)).setId(100L); return true; })
                .when(service).save(any(PayOrder.class));
        UserContext.setUser(42L);
        service.applyPayOrder(com.hmall.domain.dto.PayApplyDTO.builder().bizOrderNo(200L)
                .amount(1).payChannelCode("balance").payType(3).orderInfo("item").build());
        verify(orders).lockOrder(200L);
        verify(service).save(org.mockito.ArgumentMatchers.argThat(p -> p.getAmount() == 8500));
    }

    private Fixture fixture(PayOrder payOrder, Order order) {
        IUserService userService = mock(IUserService.class);
        IOrderService orderService = mock(IOrderService.class);
        CouponCheckoutClient couponClient = mock(CouponCheckoutClient.class);
        PayOrderServiceImpl service = spy(new PayOrderServiceImpl(userService, orderService, couponClient));
        doReturn(payOrder).when(service).getById(100L);
        doReturn(order).when(orderService).getById(200L);
        doReturn(order).when(orderService).lockOrder(200L);
        com.hmall.mapper.PayOrderMapper mapper = mock(com.hmall.mapper.PayOrderMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"baseMapper",mapper);
        org.mockito.Mockito.when(mapper.selectOne(any())).thenReturn(payOrder);
        return new Fixture(service, userService, couponClient);
    }

    private PayOrder waitingPayOrder(Long userId, Integer amount) {
        return new PayOrder()
                .setId(100L)
                .setBizOrderNo(200L)
                .setBizUserId(userId)
                .setAmount(amount)
                .setStatus(PayStatus.WAIT_BUYER_PAY.getValue());
    }

    private Order waitingOrder(Long userId, Integer amount) {
        return new Order()
                .setId(200L)
                .setUserId(userId)
                .setTotalFee(amount)
                .setStatus(1);
    }

    private PayOrderFormDTO form() {
        return PayOrderFormDTO.builder().id(100L).pw("pw").build();
    }

    private static class Fixture {
        private final PayOrderServiceImpl service;
        private final IUserService userService;
        private final CouponCheckoutClient couponClient;

        private Fixture(PayOrderServiceImpl service, IUserService userService,
                        CouponCheckoutClient couponClient) {
            this.service = service;
            this.userService = userService;
            this.couponClient = couponClient;
        }
    }
}
