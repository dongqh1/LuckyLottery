package com.hmall.service.impl;

import com.hmall.common.utils.UserContext;
import com.hmall.domain.dto.ItemDTO;
import com.hmall.domain.dto.OrderDetailDTO;
import com.hmall.domain.dto.OrderFormDTO;
import com.hmall.domain.po.Order;
import com.hmall.integration.marketing.CouponCheckoutClient;
import com.hmall.integration.marketing.CouponReservationView;
import com.hmall.integration.marketing.MembershipBenefitClient;
import com.hmall.integration.marketing.MembershipBenefitView;
import com.hmall.service.ICartService;
import com.hmall.service.IAddressService;
import com.hmall.service.IItemService;
import com.hmall.service.IOrderDetailService;
import com.hmall.service.IOrderLogisticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCouponDiscountTest {

    @AfterEach
    void clearUser() {
        UserContext.removeUser();
    }

    @Test
    void selectedCouponIsReservedAndReducesMembershipDiscountedPayableAmount() {
        IItemService itemService = mock(IItemService.class);
        IOrderDetailService detailService = mock(IOrderDetailService.class);
        ICartService cartService = mock(ICartService.class);
        IAddressService addressService = mock(IAddressService.class);
        MembershipBenefitClient membershipClient = mock(MembershipBenefitClient.class);
        CouponCheckoutClient couponClient = mock(CouponCheckoutClient.class);
        OrderServiceImpl service = org.mockito.Mockito.spy(new OrderServiceImpl(
                itemService, detailService, cartService, addressService, mock(IOrderLogisticsService.class),
                membershipClient, couponClient));

        ItemDTO item = new ItemDTO();
        item.setId(1001L);
        item.setName("旅行箱");
        item.setPrice(10_000);
        item.setStock(10);
        when(itemService.queryItemByIds(eq(Set.of(1001L)))).thenReturn(List.of(item));
        when(membershipClient.getActiveBenefit()).thenReturn(new MembershipBenefitView(
                "GOLD", 9_500, LocalDateTime.now().plusDays(30)));
        when(couponClient.reserve(eq(77L), anyLong(), any())).thenReturn(
                new CouponReservationView(77L, 20L, "全场立减 10 元", 1_000));
        doAnswer(invocation -> true).when(service).save(any(Order.class));

        UserContext.setUser(7L);
        when(addressService.getById(8L)).thenReturn(new com.hmall.domain.po.Address().setId(8L).setUserId(7L));
        OrderFormDTO form = new OrderFormDTO();
        form.setPaymentType(3);
        form.setAddressId(8L);
        form.setCouponId(77L);
        form.setDetails(List.of(new OrderDetailDTO().setItemId(1001L).setNum(1)));

        Long orderId = service.createOrder(form);

        assertThat(orderId).isNotNull();
        verify(couponClient).reserve(eq(77L), eq(orderId), eq(form.getDetails()));
        org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(service).save(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getOriginalFee()).isEqualTo(10_000);
        assertThat(saved.getMembershipDiscountFee()).isEqualTo(500);
        assertThat(saved.getCouponDiscountFee()).isEqualTo(1_000);
        assertThat(saved.getTotalFee()).isEqualTo(8_500);
        assertThat(saved.getCouponId()).isEqualTo(77L);
        assertThat(saved.getCouponTemplateId()).isEqualTo(20L);
        assertThat(saved.getCouponName()).isEqualTo("全场立减 10 元");
    }

    @Test
    void cancellingWaitingOrderReleasesItsReservedCoupon() {
        CouponCheckoutClient couponClient = mock(CouponCheckoutClient.class);
        OrderServiceImpl service = org.mockito.Mockito.spy(new OrderServiceImpl(
                mock(IItemService.class), mock(IOrderDetailService.class), mock(ICartService.class),
                mock(IAddressService.class), mock(IOrderLogisticsService.class),
                mock(MembershipBenefitClient.class), couponClient));
        Order order = new Order().setId(9001L).setUserId(7L).setStatus(1).setCouponId(77L);
        doReturn(order).when(service).lockOrder(9001L);
        doReturn(true).when(service).markOrderCancelled(order);
        UserContext.setUser(7L);

        service.cancelOrder(9001L);

        verify(couponClient).release(77L, 9001L);
    }
}
