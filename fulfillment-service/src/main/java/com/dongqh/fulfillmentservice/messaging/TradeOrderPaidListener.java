package com.dongqh.fulfillmentservice.messaging;

import com.dongqh.fulfillmentservice.service.shipping.TradeOrderShippingService;
import com.dongqh.hmapi.dto.OrderPaidMessage;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TradeOrderPaidListener {
    public static final String QUEUE = "logistics.trade.order-paid.queue";
    public static final String DEAD_LETTER_EXCHANGE = "trade.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "logistics.trade.order-paid.dlq";
    public static final String EVENT_EXCHANGE = "trade.events.topic";
    public static final String ROUTING_KEY = "trade.order.paid";

    private final TradeOrderShippingService orders;

    public TradeOrderPaidListener(TradeOrderShippingService orders) {
        this.orders = orders;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = QUEUE, durable = "true",
                    arguments = @Argument(name = "x-dead-letter-exchange", value = DEAD_LETTER_EXCHANGE)),
            exchange = @Exchange(name = EVENT_EXCHANGE, type = "topic", durable = "true"),
            key = ROUTING_KEY))
    public void onOrderPaid(OrderPaidMessage event) {
        orders.createForPaidOrder(event);
    }
}
