package com.dongqh.trade.listener;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.trade.service.TradeMembershipOrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MembershipPayStatusListener {
    private final TradeMembershipOrderService orders;

    public MembershipPayStatusListener(TradeMembershipOrderService orders) {
        this.orders = orders;
    }

    @RabbitListener(queues = "trade.membership.pay.success.queue")
    public void listen(PaySuccessMessage message) {
        orders.markPaid(message);
    }
}
