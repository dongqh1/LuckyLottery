package com.dongqh.fulfillmentservice.integration.crypto.shipping;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public final class AesGcmAddressCipher implements AddressCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final String keyVersion;
    private final byte[] key;
    private final SecureRandom random;

    public AesGcmAddressCipher(String keyVersion, String base64Key) {
        this(keyVersion, base64Key, new SecureRandom());
    }

    AesGcmAddressCipher(String keyVersion, String base64Key, SecureRandom random) {
        if (keyVersion == null || !keyVersion.matches("[A-Za-z0-9_-]{1,16}")) {
            throw new IllegalArgumentException("地址加密密钥版本不合法");
        }
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("必须配置地址加密密钥");
        }
        try {
            this.key = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("地址加密密钥必须是Base64编码", exception);
        }
        if (this.key.length != 32) {
            throw new IllegalArgumentException("地址加密密钥必须解码为32字节");
        }
        this.keyVersion = keyVersion;
        this.random = Objects.requireNonNull(random);
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("地址明文不能为空");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, nonce, plaintext.getBytes(StandardCharsets.UTF_8));
        return keyVersion + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
    }

    @Override
    public String decrypt(String envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("地址密文不能为空");
        }
        String[] parts = envelope.split("\\.", -1);
        if (parts.length != 3 || !keyVersion.equals(parts[0])) {
            throw new IllegalArgumentException("地址密文版本不受支持");
        }
        try {
            byte[] nonce = Base64.getUrlDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);
            if (nonce.length != NONCE_BYTES || ciphertext.length < TAG_BITS / 8) {
                throw new IllegalArgumentException("地址密文格式不合法");
            }
            return new String(crypt(Cipher.DECRYPT_MODE, nonce, ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw exception;
        }
    }

    private byte[] crypt(int mode, byte[] nonce, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(keyVersion.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("地址加密/解密失败", exception);
        }
    }

    @Override
    public String toString() {
        return "AesGcmAddressCipher[keyVersion=" + keyVersion + ", key=[REDACTED]]";
    }
}
