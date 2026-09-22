package com.hmall.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.UserContext;
import com.hmall.domain.dto.*;
import com.hmall.domain.po.*;
import com.hmall.domain.po.Order;
import com.hmall.domain.vo.OrderVO;
import com.hmall.integration.marketing.*;
import com.hmall.mapper.*;
import com.hmall.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrderCheckoutCouponTest {
    IOrderDetailService details = mock(IOrderDetailService.class);
    IItemService items = mock(IItemService.class);
    CouponCheckoutClient coupons = mock(CouponCheckoutClient.class);
    MembershipBenefitClient membership = mock(MembershipBenefitClient.class);
    OrderMapper mapper = mock(OrderMapper.class);
    PayOrderMapper payments = mock(PayOrderMapper.class);
    OrderServiceImpl service = spy(new OrderServiceImpl(items, details, mock(ICartService.class),
            mock(IAddressService.class), mock(IOrderLogisticsService.class), membership, coupons));
    Order order = new Order().setId(200L).setUserId(42L).setStatus(1).setTotalFee(9500)
            .setOriginalFee(10000).setMembershipDiscountFee(500).setMembershipDiscountBasisPoints(9500);
    ItemDTO item = new ItemDTO();
    PayOrder payment = new PayOrder().setId(300L).setBizOrderNo(200L).setBizUserId(42L).setStatus(1).setAmount(9500);

    @BeforeEach void setup() {
        UserContext.setUser(42L);
        doReturn(order).when(service).getById(200L);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        // Inject optional field only after implementation; RED must exercise missing behavior, not fixture setup.
        if (org.springframework.util.ReflectionUtils.findField(OrderServiceImpl.class, "payOrderMapper") != null)
            ReflectionTestUtils.setField(service, "payOrderMapper", payments);
        when(mapper.selectByIdForUpdate(200L)).thenReturn(order);
        when(details.list(any(Wrapper.class))).thenReturn(List.of(
                new OrderDetail().setOrderId(200L).setItemId(1001L).setNum(2).setPrice(5000)));
        item.setId(1001L); item.setPrice(5000); item.setStatus(1);
        when(items.queryItemByIds(any())).thenReturn(List.of(item));
        when(membership.getActiveBenefit()).thenReturn(new MembershipBenefitView("GOLD",9500,null));
        when(coupons.reserve(eq(77L), eq(200L), any())).thenReturn(new CouponReservationView(77L,20L,"coupon",1000));
        when(payments.selectList(any(Wrapper.class))).thenReturn(List.of(payment));
        when(payments.updateById(any(PayOrder.class))).thenReturn(1);
        doReturn(true).when(service).updateById(any(Order.class));
    }
    @AfterEach void cleanup() {
        UserContext.removeUser();
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void cancellationReadsCouponOnlyAfterSharedOrderLock() {
        Order stale = new Order().setId(200L).setUserId(42L).setStatus(1);
        doReturn(stale).when(service).getById(200L);
        order.setCouponId(77L);
        doReturn(true).when(service).markOrderCancelled(any(Order.class));
        service.cancelOrder(200L);
        verify(mapper).selectByIdForUpdate(200L);
        verify(coupons).release(77L,200L);
    }

    @Test void checkoutItemsComeFromOwnedOrderSnapshot() {
        assertThat(service.queryCheckoutItems(200L)).containsExactly(new OrderDetailDTO().setItemId(1001L).setNum(2));
    }
    @Test void checkoutItemsRejectOtherOwner() {
        UserContext.setUser(99L);
        assertThatThrownBy(() -> service.queryCheckoutItems(200L)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(details);
    }
    @Test void appliesCouponToSnapshotAndUpdatesExistingPaymentAmount() {
        OrderVO result = service.applyCheckoutCoupon(200L,77L);
        assertThat(result.getTotalFee()).isEqualTo(8500);
        assertThat(result.getCouponId()).isEqualTo(77L);
        assertThat(result.getOriginalFee()).isEqualTo(10000);
        verify(coupons).reserve(77L,200L,List.of(new OrderDetailDTO().setItemId(1001L).setNum(2)));
        verify(payments).updateById(argThat((PayOrder p) -> p.getId().equals(300L) && p.getAmount() == 8500));
    }
    @Test void rejectsOtherOwnerBeforeRemoteReservation() {
        UserContext.setUser(99L);
        assertThatThrownBy(() -> service.applyCheckoutCoupon(200L,77L)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(coupons);
    }
    @ParameterizedTest @ValueSource(ints={2,3,4,5,6}) void rejectsTerminalOrder(int status) {
        order.setStatus(status);
        assertThatThrownBy(() -> service.applyCheckoutCoupon(200L,77L)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(coupons);
    }
    @Test void sameCouponIsIdempotentWithoutReservingOrDiscountingAgain() {
        order.setCouponId(77L).setCouponDiscountFee(1000).setTotalFee(8500);
        assertThat(service.applyCheckoutCoupon(200L,77L).getTotalFee()).isEqualTo(8500);
        verifyNoInteractions(coupons, payments);
        verify(service,never()).updateById(any(Order.class));
    }
    @Test void cannotReplaceExistingCoupon() {
        order.setCouponId(88L);
        assertThatThrownBy(() -> service.applyCheckoutCoupon(200L,77L)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(coupons);
    }
    @Test void refusesChangedCatalogPriceRatherThanApplyingCurrentThresholdToOldSnapshot() {
        item.setPrice(6000);
        assertThatThrownBy(() -> service.applyCheckoutCoupon(200L,77L)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(coupons);
    }
    @Test void refusesChangedMembershipRatherThanRepricingExistingOrder() {
        when(membership.getActiveBenefit()).thenReturn(new MembershipBenefitView("NONE",10000,null));
        assertThatThrownBy(() -> service.applyCheckoutCoupon(200L,77L)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(coupons);
    }
    @ParameterizedTest @ValueSource(ints={2,3,9}) void refusesNonWaitingPayment(int status) {
        payment.setStatus(status);
        assertThatThrownBy(() -> service.applyCheckoutCoupon(200L,77L)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(coupons);
    }
    @ParameterizedTest @ValueSource(ints={-1,0,9500,9501}) void invalidOrZeroPayableDiscountIsReleased(int discount) {
        when(coupons.reserve(eq(77L),eq(200L),any())).thenReturn(new CouponReservationView(77L,20L,"coupon",discount));
        assertThatThrownBy(() -> service.applyCheckoutCoupon(200L,77L)).isInstanceOf(BadRequestException.class);
        verify(coupons).release(77L,200L);
    }
    @Test void timeoutStillReleasesRequestedCouponBinding() {
        when(coupons.reserve(eq(77L),eq(200L),any())).thenThrow(new BadRequestException("timeout"));
        assertThatThrownBy(() -> service.applyCheckoutCoupon(200L,77L)).hasMessage("timeout");
        verify(coupons).release(77L,200L);
    }
    @Test void failedLocalUpdateReleasesReservation() {
        doReturn(false).when(service).updateById(any(Order.class));
        assertThatThrownBy(() -> service.applyCheckoutCoupon(200L,77L)).isInstanceOf(BadRequestException.class);
        verify(coupons).release(77L,200L);
    }
    @Test void failedPaymentAmountUpdateReleasesReservation() {
        when(payments.updateById(any(PayOrder.class))).thenReturn(0);
        assertThatThrownBy(() -> service.applyCheckoutCoupon(200L,77L)).isInstanceOf(BadRequestException.class);
        verify(coupons).release(77L,200L);
    }
    @Test void commitFailureWindowReleasesButSuccessfulCommitDoesNot() {
        TransactionSynchronizationManager.initSynchronization();
        service.applyCheckoutCoupon(200L,77L);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronization callback = TransactionSynchronizationManager.getSynchronizations().get(0);
        callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(coupons,never()).release(anyLong(),anyLong());
        callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(coupons).release(77L,200L);
    }
}
