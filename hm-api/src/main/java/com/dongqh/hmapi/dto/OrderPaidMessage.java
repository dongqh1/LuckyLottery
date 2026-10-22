package com.dongqh.hmapi.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Published after a cash order payment is committed. Contains identifiers only. */
public record OrderPaidMessage(UUID eventId, Long orderId, Long userId, LocalDateTime paidAt) {
    public OrderPaidMessage {
        if (eventId == null || orderId == null || orderId <= 0 || userId == null || userId <= 0 || paidAt == null) {
            throw new IllegalArgumentException("order-paid event is invalid");
        }
    }
}
