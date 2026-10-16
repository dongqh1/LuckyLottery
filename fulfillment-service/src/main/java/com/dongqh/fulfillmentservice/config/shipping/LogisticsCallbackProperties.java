package com.dongqh.fulfillmentservice.config.shipping;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "luckyhub.fulfillment.shipping-callback")
public record LogisticsCallbackProperties(String secret, Duration window) {

    public LogisticsCallbackProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("fulfillment shipping callback secret must contain at least 32 UTF-8 bytes");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("fulfillment shipping callback window must be positive");
        }
    }

    @Override
    public String toString() {
        return "LogisticsCallbackProperties[secret=REDACTED, window=" + window + "]";
    }
}
