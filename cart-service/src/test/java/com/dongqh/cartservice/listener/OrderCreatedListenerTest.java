package com.dongqh.cartservice.listener;

import com.dongqh.cartservice.service.ICartService;
import com.dongqh.hmapi.dto.OrderCreatedMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

class OrderCreatedListenerTest {

    @Test
    void removesOnlyTheOrderingUsersCheckoutSnapshot() {
        ICartService carts = mock(ICartService.class);
        OrderCreatedListener listener = new OrderCreatedListener(carts);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 17, 12, 0);
        OrderCreatedMessage message = new OrderCreatedMessage(
                UUID.randomUUID(), 101L, 7L, List.of(11L, 12L), occurredAt);

        listener.onOrderCreated(message);

        verify(carts).removeOrderedItems(7L, List.of(11L, 12L), occurredAt);
    }

    @Test
    void declaresDurableQueueBindingAndDeadLetterExchange() throws Exception {
        RabbitListener listener = OrderCreatedListener.class
                .getMethod("onOrderCreated", OrderCreatedMessage.class)
                .getAnnotation(RabbitListener.class);

        assertThat(listener.bindings()).hasSize(1);
        var binding = listener.bindings()[0];
        assertThat(binding.value().name()).isEqualTo("cart.trade.order-created.queue");
        assertThat(binding.value().durable()).isEqualTo("true");
        assertThat(binding.exchange().name()).isEqualTo("trade.events.topic");
        assertThat(binding.key()).containsExactly("trade.order.created");
        assertThat(binding.value().arguments()).anySatisfy(argument -> {
            assertThat(argument.name()).isEqualTo("x-dead-letter-exchange");
            assertThat(argument.value()).isEqualTo("trade.events.dlx");
        });
    }
}
