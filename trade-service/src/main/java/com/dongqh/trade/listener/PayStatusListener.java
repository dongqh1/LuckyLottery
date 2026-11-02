package com.dongqh.trade.listener;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayStatusListener {

    private final IOrderService orderService;

    @RabbitListener(queues = "trade.pay.success.queue")
    public void listenPaySuccess(PaySuccessMessage message) {
        orderService.markOrderPaySuccess(
                message.orderId(),
                message.userId(),
                message.amount(),
                message.paidAt()
        );
    }

}
