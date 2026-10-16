package com.dongqh.fulfillmentservice.shipping;

import com.dongqh.fulfillmentservice.config.shipping.LogisticsCallbackProperties;
import com.dongqh.fulfillmentservice.integration.crypto.shipping.LogisticsCallbackSigner;
import com.dongqh.fulfillmentservice.enums.shipping.TrackingEventType;
import com.dongqh.fulfillmentservice.domain.model.shipping.LogisticsCallbackCommand;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsCallbackSignerTests {

    private final LogisticsCallbackSigner signer =
            new LogisticsCallbackSigner("test-callback-secret-32-bytes-long");

    @Test
    void canonicalPayloadAndBase64UrlHmacRemainCompatible() {
        LogisticsCallbackCommand command = new LogisticsCallbackCommand(
                "callback-1", "nonce-1", 1786500930L, "SIM-L-123",
                TrackingEventType.IN_TRANSIT, LocalDateTime.parse("2026-08-12T10:15:30.123"),
                "杭州中转站", "运输中", null);

        assertThat(signer.canonical(command)).isEqualTo(
                "callback-1\nnonce-1\n1786500930\nSIM-L-123\nIN_TRANSIT\n2026-08-12T10:15:30.123");
        assertThat(signer.sign(command)).isEqualTo("ZBHOGNuoj3qW9fP8pcVtKGdeHzFxeyoly2k2aLfaOR8");
    }

    @Test
    void verificationUsesConstantTimeComparisonAndRejectsMalformedSignatures() {
        LogisticsCallbackCommand unsigned = new LogisticsCallbackCommand(
                "callback-1", "nonce-1", 1786500930L, "SIM-L-123",
                TrackingEventType.IN_TRANSIT, LocalDateTime.parse("2026-08-12T10:15:30.123"),
                null, null, null);
        LogisticsCallbackCommand signed = withSignature(unsigned);
        LogisticsCallbackCommand tampered = new LogisticsCallbackCommand(
                signed.callbackId(), signed.nonce(), signed.timestampEpochSecond(), signed.waybillNo(),
                TrackingEventType.DELIVERED, signed.eventTime(), signed.locationSummary(),
                signed.description(), signed.signature());
        LogisticsCallbackCommand malformed = new LogisticsCallbackCommand(
                signed.callbackId(), signed.nonce(), signed.timestampEpochSecond(), signed.waybillNo(),
                signed.eventType(), signed.eventTime(), signed.locationSummary(),
                signed.description(), "not-base64-url!");

        assertThat(signer.verify(signed)).isTrue();
        assertThat(signer.verify(tampered)).isFalse();
        assertThat(signer.verify(malformed)).isFalse();
    }

    @Test
    void callbackSecretIsNotExposedByConfigurationRepresentation() {
        LogisticsCallbackProperties properties = new LogisticsCallbackProperties(
                "test-callback-secret-32-bytes-long", java.time.Duration.ofMinutes(5));

        assertThat(properties.toString()).contains("REDACTED").doesNotContain("test-callback-secret");
    }

    private LogisticsCallbackCommand withSignature(LogisticsCallbackCommand command) {
        return new LogisticsCallbackCommand(command.callbackId(), command.nonce(), command.timestampEpochSecond(),
                command.waybillNo(), command.eventType(), command.eventTime(), command.locationSummary(),
                command.description(), signer.sign(command));
    }
}
