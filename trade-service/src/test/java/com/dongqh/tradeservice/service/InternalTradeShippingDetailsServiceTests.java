package com.dongqh.tradeservice.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dongqh.tradeservice.domain.po.Order;
import com.dongqh.tradeservice.domain.po.OrderDetail;
import com.dongqh.tradeservice.domain.po.OrderLogistics;
import com.dongqh.tradeservice.mapper.OrderMapper;
import com.hmall.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalTradeShippingDetailsServiceTests {
    @Test
    void returnsFrozenAddressAndAggregatedOrderLineDetailsAfterPayment() {
        OrderMapper orders = mock(OrderMapper.class);
        IOrderLogisticsService logistics = mock(IOrderLogisticsService.class);
        IOrderDetailService details = mock(IOrderDetailService.class);
        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 21, 12, 0);
        when(orders.selectById(101L)).thenReturn(new Order().setId(101L).setUserId(7L)
                .setStatus(2).setPayTime(paidAt));
        when(logistics.getById(101L)).thenReturn(new OrderLogistics().setContact("林女士")
                .setMobile("13800001111").setProvince("浙江省").setCity("杭州市")
                .setTown("西湖区").setStreet("文三路 1 号"));
        when(details.list(any(Wrapper.class))).thenReturn(List.of(
                new OrderDetail().setName("茶杯").setNum(2).setImage("cup.png"),
                new OrderDetail().setName("茶壶").setNum(1).setImage("pot.png")));

        var result = new InternalTradeShippingDetailsService(orders, logistics, details).getPaidOrder(101L);

        assertThat(result.orderId()).isEqualTo(101L);
        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.paidAt()).isEqualTo(paidAt);
        assertThat(result.contact()).isEqualTo("林女士");
        assertThat(result.mobile()).isEqualTo("13800001111");
        assertThat(result.street()).isEqualTo("文三路 1 号");
        assertThat(result.skuCode()).isEqualTo("TRADE-ORDER-101");
        assertThat(result.productName()).isEqualTo("茶杯 × 2、茶壶 × 1");
        assertThat(result.quantity()).isEqualTo(3);
        assertThat(result.imageUrl()).isEqualTo("cup.png");
    }

    @Test
    void refusesToExposeShippingDetailsForAnUnpaidOrder() {
        OrderMapper orders = mock(OrderMapper.class);
        IOrderLogisticsService logistics = mock(IOrderLogisticsService.class);
        IOrderDetailService details = mock(IOrderDetailService.class);
        when(orders.selectById(101L)).thenReturn(new Order().setId(101L).setUserId(7L).setStatus(1));

        assertThatThrownBy(() -> new InternalTradeShippingDetailsService(orders, logistics, details)
                .getPaidOrder(101L)).isInstanceOf(BadRequestException.class);

        verifyNoInteractions(logistics, details);
    }
}
