package com.dongqh.hmapi.dto;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable payment-success fact shared by downstream services.
 *
 * @param eventId globally unique event identifier
 * @param orderId business order ID
 * @param userId user who owns the order
 * @param amount paid amount in cents
 * @param couponId locked coupon to consume, or {@code null}
 * @param paidAt authoritative payment time
 */
public record PaySuccessMessage(
        String eventId,
        Long orderId,
        Long userId,
        Integer amount,
        Long couponId,
        LocalDateTime paidAt
) {
    public PaySuccessMessage {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (couponId != null && couponId <= 0) {
            throw new IllegalArgumentException("couponId must be positive when present");
        }
        eventId = eventId.trim();
        Objects.requireNonNull(paidAt, "paidAt must not be null");
    }
}
