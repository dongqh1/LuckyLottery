package com.dongqh.hmapi.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Event emitted after a trade order and its stock reservation are committed. */
public record OrderCreatedMessage(
        UUID eventId,
        Long orderId,
        Long userId,
        List<Long> itemIds,
        LocalDateTime occurredAt) {

    public OrderCreatedMessage {
        itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
    }
}
