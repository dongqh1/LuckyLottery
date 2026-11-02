package com.dongqh.trade.service.impl;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.PayClient;
import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmapi.dto.AddressDTO;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.hmapi.dto.OrderDetailDTO;
import com.dongqh.trade.domain.dto.OrderFormDTO;
import com.dongqh.trade.domain.po.Order;
import com.dongqh.trade.marketing.CouponCheckoutClient;
import com.dongqh.trade.marketing.MembershipBenefitClient;
import com.dongqh.trade.marketing.MembershipBenefitView;
import com.dongqh.trade.messaging.TradeOutboxService;
import com.dongqh.trade.service.IOrderDetailService;
import com.dongqh.trade.service.IOrderLogisticsService;
import com.hmall.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCreationUserContextTest {

    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    @Test
    void createsOrderForCurrentUser() {
        ItemClient itemClient = mock(ItemClient.class);
        IOrderDetailService detailService = mock(IOrderDetailService.class);
        TradeOutboxService outbox = mock(TradeOutboxService.class);
        UserClient userClient = mock(UserClient.class);
        IOrderLogisticsService logisticsService = mock(IOrderLogisticsService.class);
        MembershipBenefitClient membershipClient = mock(MembershipBenefitClient.class);
        OrderServiceImpl service = spy(new OrderServiceImpl(
                itemClient,
                mock(PayClient.class),
                detailService,
                outbox,
                userClient,
                logisticsService,
                membershipClient,
                mock(CouponCheckoutClient.class)));

        AddressDTO address = new AddressDTO();
        address.setId(8L);
        address.setUserId(7L);
        address.setContact("测试用户");
        when(userClient.findAddressById(8L)).thenReturn(address);

        ItemDTO item = new ItemDTO();
        item.setId(1001L);
        item.setName("旅行箱");
        item.setPrice(10_000);
        when(itemClient.queryItemByIds(eq(Set.of(1001L)))).thenReturn(List.of(item));
        when(membershipClient.getActiveBenefit()).thenReturn(
                new MembershipBenefitView("NORMAL", 10_000, LocalDateTime.now().plusDays(30)));
        doAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(9001L);
            return true;
        }).when(service).save(any(Order.class));

        UserContext.setUser(7L);
        OrderFormDTO form = new OrderFormDTO();
        form.setAddressId(8L);
        form.setPaymentType(3);
        form.setDetails(List.of(new OrderDetailDTO().setItemId(1001L).setNum(1)));

        assertThat(service.createOrder(form)).isEqualTo(9001L);
        ArgumentCaptor<Order> savedOrder = ArgumentCaptor.forClass(Order.class);
        verify(service).save(savedOrder.capture());
        assertThat(savedOrder.getValue().getUserId()).isEqualTo(7L);
        ArgumentCaptor<com.dongqh.hmapi.dto.OrderCreatedMessage> event =
                ArgumentCaptor.forClass(com.dongqh.hmapi.dto.OrderCreatedMessage.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().orderId()).isEqualTo(9001L);
        assertThat(event.getValue().userId()).isEqualTo(7L);
        assertThat(event.getValue().itemIds()).containsExactly(1001L);
    }
}
