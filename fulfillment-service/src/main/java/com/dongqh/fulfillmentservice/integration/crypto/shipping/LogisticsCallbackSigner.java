package com.dongqh.fulfillmentservice.integration.crypto.shipping;

import com.dongqh.fulfillmentservice.config.shipping.LogisticsCallbackProperties;
import com.dongqh.fulfillmentservice.domain.model.shipping.LogisticsCallbackCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Component
public final class LogisticsCallbackSigner {
    private static final String ALGORITHM = "HmacSHA256";
    private static final DateTimeFormatter EVENT_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final byte[] secret;

    @Autowired
    public LogisticsCallbackSigner(LogisticsCallbackProperties properties) {
        this(properties.secret());
    }

    public LogisticsCallbackSigner(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("fulfillment shipping callback secret must contain at least 32 UTF-8 bytes");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8).clone();
    }

    public String canonical(LogisticsCallbackCommand command) {
        return command.callbackId() + '\n'
                + command.nonce() + '\n'
                + command.timestampEpochSecond() + '\n'
                + command.waybillNo() + '\n'
                + command.eventType().name() + '\n'
                + EVENT_TIME_FORMAT.format(command.eventTime());
    }

    public String sign(LogisticsCallbackCommand command) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac(canonical(command)));
    }

    public boolean verify(LogisticsCallbackCommand command) {
        byte[] supplied;
        try {
            supplied = Base64.getUrlDecoder().decode(command.signature());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return supplied.length == 32 && MessageDigest.isEqual(mac(canonical(command)), supplied);
    }

    private byte[] mac(String canonical) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256不可用", exception);
        }
    }
}
