package com.dongqh.fulfillmentservice.shipping.service;

import com.dongqh.fulfillmentservice.shipping.config.LogisticsCallbackProperties;
import com.dongqh.fulfillmentservice.shipping.crypto.LogisticsCallbackSigner;
import com.dongqh.fulfillmentservice.shipping.domain.LogisticsCallbackErrorCode;
import com.dongqh.fulfillmentservice.shipping.domain.LogisticsCallbackException;
import com.dongqh.fulfillmentservice.shipping.model.LogisticsCallbackCommand;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class LogisticsCallbackService {
    private static final Pattern SIGNED_IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}");

    private final LogisticsCallbackSigner signer;
    private final LogisticsCallbackProperties properties;
    private final TransactionalLogisticsCallbackProcessor processor;
    private final Clock clock;

    public LogisticsCallbackService(
            LogisticsCallbackSigner signer,
            LogisticsCallbackProperties properties,
            TransactionalLogisticsCallbackProcessor processor,
            Clock fulfillmentClock
    ) {
        this.signer = signer;
        this.properties = properties;
        this.processor = processor;
        this.clock = fulfillmentClock;
    }

    public void handle(LogisticsCallbackCommand command) {
        validate(command);
        if (!withinWindow(command.timestampEpochSecond()) || !signer.verify(command)) {
            throw new LogisticsCallbackException(LogisticsCallbackErrorCode.CALLBACK_SIGNATURE_INVALID);
        }
        LogisticsCallbackErrorCode failure = processor.process(command);
        if (failure != null) {
            throw new LogisticsCallbackException(failure);
        }
    }

    private boolean withinWindow(long timestamp) {
        long now = Instant.now(clock).getEpochSecond();
        long window = properties.window().getSeconds();
        return timestamp >= now - window && timestamp <= now + window;
    }

    private void validate(LogisticsCallbackCommand command) {
        if (command == null
                || invalidSignedIdentifier(command.callbackId())
                || invalidSignedIdentifier(command.nonce())
                || invalidSignedIdentifier(command.waybillNo())
                || command.eventType() == null
                || command.eventTime() == null
                || invalidOptional(command.locationSummary(), 200)
                || invalidOptional(command.description(), 500)
                || invalid(command.signature(), 100)) {
            throw new LogisticsCallbackException(LogisticsCallbackErrorCode.SHIPPING_REQUEST_INVALID);
        }
    }

    private boolean invalid(String value, int max) {
        return value == null || value.isBlank() || value.length() > max;
    }

    private boolean invalidSignedIdentifier(String value) {
        return value == null || !SIGNED_IDENTIFIER.matcher(value).matches();
    }

    private boolean invalidOptional(String value, int max) {
        return value != null && value.length() > max;
    }
}
