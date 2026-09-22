package com.dongqh.cartservice.listener;

import com.dongqh.cartservice.service.ICartService;
import com.dongqh.hmapi.dto.OrderCreatedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCreatedListener {
    public static final String EXCHANGE = "trade.events.topic";
    public static final String DLX = "trade.events.dlx";
    public static final String QUEUE = "cart.trade.order-created.queue";

    private final ICartService carts;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = QUEUE, durable = "true", arguments =
                    @Argument(name = "x-dead-letter-exchange", value = DLX)),
            exchange = @Exchange(name = EXCHANGE, type = "topic", durable = "true"),
            key = "trade.order.created"))
    public void onOrderCreated(OrderCreatedMessage message) {
        carts.removeOrderedItems(message.userId(), message.itemIds(), message.occurredAt());
    }
}
