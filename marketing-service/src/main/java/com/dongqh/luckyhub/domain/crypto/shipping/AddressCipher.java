package com.dongqh.luckyhub.domain.crypto.shipping;

public interface AddressCipher {
    String encrypt(String plaintext);
    String decrypt(String envelope);
}
