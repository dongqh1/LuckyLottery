package com.dongqh.luckyhub.lottery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "luckyhub.messaging")
public record MessagingProperties(
        boolean enabled,
        String logicalConsumerName,
        Duration publisherConfirmTimeout,
        Duration outboxLease
) {
    public MessagingProperties {
        if (!hasText(logicalConsumerName)) {
            throw new IllegalArgumentException("logicalConsumerName must not be blank");
        }
        requirePositive(publisherConfirmTimeout, "publisherConfirmTimeout");
        requirePositive(outboxLease, "outboxLease");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
