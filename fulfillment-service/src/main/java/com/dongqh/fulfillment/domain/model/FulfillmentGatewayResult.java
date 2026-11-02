package com.dongqh.fulfillment.domain.model;

import com.dongqh.fulfillment.enums.GatewayOutcome;

import java.util.Objects;

public record FulfillmentGatewayResult(
        GatewayOutcome outcome,
        String externalReference,
        String errorCode,
        String safeMessage
) {

    public FulfillmentGatewayResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        externalReference = optional(externalReference);
        errorCode = bounded(errorCode, 64);
        safeMessage = bounded(safeMessage, 500);
        if (outcome == GatewayOutcome.SUCCEEDED && externalReference == null) {
            throw new IllegalArgumentException("成功结果必须包含externalReference");
        }
    }

    public static FulfillmentGatewayResult succeeded(String reference) {
        return new FulfillmentGatewayResult(GatewayOutcome.SUCCEEDED, reference, null, null);
    }

    public static FulfillmentGatewayResult retryable(String code, String message) {
        return new FulfillmentGatewayResult(GatewayOutcome.RETRYABLE_FAILURE, null, code, message);
    }

    public static FulfillmentGatewayResult permanent(String code, String message) {
        return new FulfillmentGatewayResult(GatewayOutcome.PERMANENT_FAILURE, null, code, message);
    }

    public static FulfillmentGatewayResult unknown(String code, String message) {
        return new FulfillmentGatewayResult(GatewayOutcome.UNKNOWN, null, code, message);
    }

    public static FulfillmentGatewayResult notFound() {
        return new FulfillmentGatewayResult(GatewayOutcome.NOT_FOUND, null, null, null);
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String bounded(String value, int maxLength) {
        String normalized = optional(value);
        return normalized == null ? null : normalized.substring(0, Math.min(maxLength, normalized.length()));
    }
}
