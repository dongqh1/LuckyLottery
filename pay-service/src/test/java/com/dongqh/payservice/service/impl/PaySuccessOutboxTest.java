package com.dongqh.payservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dongqh.hmapi.client.TradeClient;
import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmapi.dto.OrderPaymentDTO;
import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.payservice.domain.dto.PayOrderFormDTO;
import com.dongqh.payservice.domain.po.PayOrder;
import com.dongqh.payservice.enums.PayStatus;
import com.dongqh.payservice.mapper.PayOrderMapper;
import com.dongqh.payservice.mapper.PayOrderDomainMapper;
import com.dongqh.payservice.messaging.PayOutboxService;
import com.hmall.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaySuccessOutboxTest {
    @AfterEach
    void clearUser() {
        UserContext.removeUser();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void appendsAuthoritativePaymentSnapshotToOutbox() {
        UserClient users = mock(UserClient.class);
        TradeClient trades = mock(TradeClient.class);
        PayOrderMapper payments = mock(PayOrderMapper.class);
        PayOrderDomainMapper paymentDomains = mock(PayOrderDomainMapper.class);
        PayOutboxService outbox = mock(PayOutboxService.class);
        PayOrderServiceImpl service = spy(new PayOrderServiceImpl(users, trades, payments, paymentDomains, outbox));
        ReflectionTestUtils.setField(service, "baseMapper", payments);

        PayOrder payment = new PayOrder().setId(501L).setBizOrderNo(101L)
                .setBizUserId(7L).setAmount(8500)
                .setStatus(PayStatus.WAIT_BUYER_PAY.getValue());
        OrderPaymentDTO order = new OrderPaymentDTO();
        order.setId(101L);
        order.setUserId(7L);
        order.setStatus(1);
        order.setTotalFee(8500);
        order.setCouponId(31L);
        UserContext.setUser(7L);
        doReturn(payment).when(service).getById(501L);
        when(trades.queryOrderForPayment(101L, 7L)).thenReturn(order);
        when(payments.selectOne(any(Wrapper.class))).thenReturn(payment);
        doReturn(true).when(service).markPayOrderSuccess(eq(501L), any());

        service.tryPayOrderByBalance(PayOrderFormDTO.builder().id(501L).pw("123456").build());

        verify(users).deductMoney("123456", 8500);
        ArgumentCaptor<PaySuccessMessage> event = ArgumentCaptor.forClass(PaySuccessMessage.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventId()).isNotBlank();
        assertThat(event.getValue().orderId()).isEqualTo(101L);
        assertThat(event.getValue().userId()).isEqualTo(7L);
        assertThat(event.getValue().amount()).isEqualTo(8500);
        assertThat(event.getValue().couponId()).isEqualTo(31L);
        assertThat(event.getValue().paidAt()).isNotNull();
    }
}
