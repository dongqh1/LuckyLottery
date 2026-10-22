package com.dongqh.lotteryservice.outbox.event;

import java.util.Objects;

public record PrizeFulfillmentRequestedEvent(Long benefitId, Long drawRecordId, Long prizeId) {
    public PrizeFulfillmentRequestedEvent {
        requirePositive(benefitId, "benefitId");
        requirePositive(drawRecordId, "drawRecordId");
        requirePositive(prizeId, "prizeId");
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
