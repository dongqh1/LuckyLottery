package com.hmall.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import com.hmall.common.utils.UserContext;
import com.hmall.domain.dto.ItemDTO;
import com.hmall.domain.dto.OrderDetailDTO;
import com.hmall.domain.dto.OrderFormDTO;
import com.hmall.domain.po.Address;
import com.hmall.domain.po.Order;
import com.hmall.domain.po.OrderLogistics;
import com.hmall.domain.vo.OrderVO;
import com.hmall.integration.marketing.CouponCheckoutClient;
import com.hmall.integration.marketing.MembershipBenefitClient;
import com.hmall.integration.marketing.MembershipBenefitView;
import com.hmall.service.IAddressService;
import com.hmall.service.ICartService;
import com.hmall.service.IItemService;
import com.hmall.service.IOrderDetailService;
import com.hmall.service.IOrderLogisticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceDeliveryTest {

    @Mock
    private IItemService itemService;
    @Mock
    private IOrderDetailService detailService;
    @Mock
    private ICartService cartService;
    @Mock
    private IOrderLogisticsService logisticsService;
    @Mock
    private IAddressService addressService;
    @Mock
    private MembershipBenefitClient membershipBenefitClient;
    @Mock
    private CouponCheckoutClient couponCheckoutClient;
    @Spy
    @InjectMocks
    private OrderServiceImpl orderService;

    @AfterEach
    void clearUser() {
        UserContext.removeUser();
    }

    @Test
    void returnsCarrierTrackingNumberAndAddressSnapshotFromHmallLogistics() {
        Order order = new Order().setId(42L).setUserId(7L).setStatus(3);
        OrderLogistics logistics = new OrderLogistics()
                .setOrderId(42L)
                .setLogisticsCompany("顺丰速运")
                .setLogisticsNumber("SF1234567890")
                .setContact("李四")
                .setMobile("13838411438")
                .setProvince("上海")
                .setCity("上海")
                .setTown("浦东新区")
                .setStreet("航头镇航头路 1 号");
        UserContext.setUser(7L);
        doReturn(order).when(orderService).getById(42L);
        when(logisticsService.getById(42L)).thenReturn(logistics);

        OrderVO result = orderService.queryOrderById(42L);

        assertThat(result.getDelivery().getCarrier()).isEqualTo("顺丰速运");
        assertThat(result.getDelivery().getTrackingNumber()).isEqualTo("SF1234567890");
        assertThat(result.getDelivery().getAddressSnapshot().getContact()).isEqualTo("李四");
        assertThat(result.getDelivery().getAddressSnapshot().getFullAddress())
                .isEqualTo("上海上海浦东新区航头镇航头路 1 号");
    }

    @Test
    void freezesOwnedAddressIntoOrderLogisticsWhenCreatingOrder() {
        ItemDTO item = new ItemDTO();
        item.setId(1001L);
        item.setName("RIMOWA 旅行箱");
        item.setPrice(1300);
        when(itemService.queryItemByIds(eq(Set.of(1001L)))).thenReturn(List.of(item));
        when(membershipBenefitClient.getActiveBenefit()).thenReturn(
                new MembershipBenefitView("GOLD", 9500, LocalDateTime.now().plusDays(30)));
        Address address = new Address().setId(8L).setUserId(7L).setContact("李四")
                .setMobile("13838411438").setProvince("上海").setCity("上海")
                .setTown("浦东新区").setStreet("航头镇航头路 1 号");
        when(addressService.getById(8L)).thenReturn(address);
        doAnswer(invocation -> true).when(orderService).save(any(Order.class));
        UserContext.setUser(7L);
        OrderFormDTO form = new OrderFormDTO();
        form.setAddressId(8L);
        form.setPaymentType(3);
        form.setDetails(List.of(new OrderDetailDTO().setItemId(1001L).setNum(1)));

        Long orderId = orderService.createOrder(form);

        verify(addressService).getById(8L);
        org.mockito.ArgumentCaptor<OrderLogistics> captor =
                org.mockito.ArgumentCaptor.forClass(OrderLogistics.class);
        verify(logisticsService).save(captor.capture());
        OrderLogistics snapshot = captor.getValue();
        assertThat(snapshot.getOrderId()).isEqualTo(orderId);
        assertThat(snapshot.getContact()).isEqualTo("李四");
        assertThat(snapshot.getMobile()).isEqualTo("13838411438");
        assertThat(snapshot.getProvince()).isEqualTo("上海");
        assertThat(snapshot.getCity()).isEqualTo("上海");
        assertThat(snapshot.getTown()).isEqualTo("浦东新区");
        assertThat(snapshot.getStreet()).isEqualTo("航头镇航头路 1 号");
    }

    @Test
    void rejectsAddressOwnedByAnotherUserBeforeCreatingOrder() {
        when(addressService.getById(8L)).thenReturn(new Address().setId(8L).setUserId(9L));
        UserContext.setUser(7L);
        OrderFormDTO form = new OrderFormDTO();
        form.setAddressId(8L);
        form.setDetails(List.of(new OrderDetailDTO().setItemId(1001L).setNum(1)));

        assertThatThrownBy(() -> orderService.createOrder(form))
                .isInstanceOf(com.hmall.common.exception.BadRequestException.class)
                .hasMessage("收货地址不存在");
        verify(orderService, never()).save(any(Order.class));
        verify(logisticsService, never()).save(any(OrderLogistics.class));
    }

    @Test
    void rejectsReadingAnotherUsersOrder() {
        doReturn(new Order().setId(9001L).setUserId(9L)).when(orderService).getById(9001L);
        UserContext.setUser(7L);

        assertThatThrownBy(() -> orderService.queryOrderById(9001L))
                .isInstanceOf(com.hmall.common.exception.BadRequestException.class)
                .hasMessage("订单不存在");
        verify(logisticsService, never()).getById(9001L);
    }

    @Test
    void queriesOnlyCurrentUsersOrdersByStatusNewestFirst() {
        UserContext.setUser(7L);
        doAnswer(invocation -> {
            Page<Order> page = invocation.getArgument(0);
            AbstractWrapper<Order, ?, ?> wrapper = invocation.getArgument(1);
            assertThat(wrapper.getSqlSegment()).contains("user_id", "status", "create_time");
            assertThat(wrapper.getParamNameValuePairs().values()).contains(7L, 2);
            page.setTotal(2);
            page.setRecords(List.of(
                    new Order().setId(102L).setUserId(7L).setStatus(2).setTotalFee(1200),
                    new Order().setId(101L).setUserId(7L).setStatus(2).setTotalFee(900)));
            return page;
        }).when(orderService).page(any(Page.class), any(AbstractWrapper.class));

        PageDTO<OrderVO> result = orderService.queryMyOrders(
                new PageQuery().setPageNo(1).setPageSize(20), 2);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getList()).extracting(OrderVO::getId).containsExactly(102L, 101L);
        assertThat(result.getList()).allSatisfy(order -> {
            assertThat(order.getUserId()).isEqualTo(7L);
            assertThat(order.getStatus()).isEqualTo(2);
        });
    }
}
