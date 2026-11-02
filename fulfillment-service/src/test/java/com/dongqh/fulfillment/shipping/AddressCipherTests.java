package com.dongqh.fulfillment.shipping;

import com.dongqh.fulfillment.integration.crypto.shipping.AesGcmAddressCipher;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressCipherTests {

    @Test
    void encryptsWithRandomNonceAndRoundTripsWithoutExposingPlaintext() {
        AesGcmAddressCipher cipher = cipher();

        String first = cipher.encrypt("Receiver Name");
        String second = cipher.encrypt("Receiver Name");

        assertThat(first).startsWith("v1.").isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("Receiver Name");
        assertThat(cipher.toString()).doesNotContain(Base64.getEncoder().encodeToString(key()));
    }

    @Test
    void rejectsTamperedCiphertextAndInvalidKeys() {
        AesGcmAddressCipher cipher = cipher();
        String encrypted = cipher.encrypt("private value");

        assertThatThrownBy(() -> cipher.decrypt(encrypted + "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AesGcmAddressCipher("v1", "not-base64"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AesGcmAddressCipher cipher() {
        return new AesGcmAddressCipher("v1", Base64.getEncoder().encodeToString(key()));
    }

    private static byte[] key() {
        return "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
