package com.dongqh.tradeservice.service.impl;

import com.dongqh.tradeservice.domain.po.Order;
import com.dongqh.tradeservice.mapper.OrderMapper;
import com.dongqh.tradeservice.messaging.TradeOutboxService;
import com.dongqh.hmapi.dto.OrderPaidMessage;
import com.hmall.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PaySuccessIdempotencyTest {
    private final OrderMapper mapper = mock(OrderMapper.class);
    private final TradeOutboxService outbox = mock(TradeOutboxService.class);
    private final OrderServiceImpl service = new OrderServiceImpl(
            null, null, null, null, null, null, null, null);
    private final LocalDateTime paidAt = LocalDateTime.of(2026, 9, 17, 12, 30);

    @BeforeEach
    void setUp() {
        reset(mapper);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "tradeOutboxService", outbox);
    }

    @Test
    void changesMatchingPendingOrderToPaidUsingEventTime() {
        Order order = order(1);
        when(mapper.selectByIdForUpdate(101L)).thenReturn(order);
        when(mapper.updateById(order)).thenReturn(1);

        service.markOrderPaySuccess(101L, 7L, 8500, paidAt);

        verify(mapper).updateById(order);
        verify(outbox).append(org.mockito.ArgumentMatchers.<OrderPaidMessage>argThat(event -> event.orderId().equals(101L)
                && event.userId().equals(7L) && event.paidAt().equals(paidAt)));
        org.assertj.core.api.Assertions.assertThat(order.getStatus()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(order.getPayTime()).isEqualTo(paidAt);
    }

    @Test
    void acceptsMatchingDuplicateWithoutWritingAgain() {
        when(mapper.selectByIdForUpdate(101L)).thenReturn(order(2).setPayTime(paidAt));

        service.markOrderPaySuccess(101L, 7L, 8500, paidAt.plusSeconds(1));

        verify(mapper, never()).updateById(any(Order.class));
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsMismatchedUserAmountAndInvalidState() {
        when(mapper.selectByIdForUpdate(101L))
                .thenReturn(order(1).setUserId(8L))
                .thenReturn(order(1).setTotalFee(8600))
                .thenReturn(order(5));

        assertThatThrownBy(() -> service.markOrderPaySuccess(101L, 7L, 8500, paidAt))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.markOrderPaySuccess(101L, 7L, 8500, paidAt))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.markOrderPaySuccess(101L, 7L, 8500, paidAt))
                .isInstanceOf(BadRequestException.class);
    }

    private Order order(int status) {
        return new Order().setId(101L).setUserId(7L).setTotalFee(8500).setStatus(status);
    }
}
