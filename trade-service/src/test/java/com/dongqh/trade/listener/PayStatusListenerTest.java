package com.dongqh.trade.listener;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.trade.service.IOrderService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PayStatusListenerTest {

    @Test
    void delegatesAllFieldsFromPaySuccessMessage() {
        IOrderService orderService = mock(IOrderService.class);
        PayStatusListener listener = new PayStatusListener(orderService);

        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 17, 12, 30);

        listener.listenPaySuccess(new PaySuccessMessage(
                "evt-pay-101", 101L, 7L, 8_500, 31L, paidAt));

        verify(orderService).markOrderPaySuccess(101L, 7L, 8_500, paidAt);
    }
}
