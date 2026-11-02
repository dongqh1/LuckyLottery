package com.dongqh.fulfillment.integration.crypto.shipping;

public interface AddressCipher {
    String encrypt(String plaintext);

    String decrypt(String envelope);
}
