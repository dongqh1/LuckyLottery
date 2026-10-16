package com.dongqh.fulfillmentservice.integration.crypto.shipping;

public interface AddressCipher {
    String encrypt(String plaintext);

    String decrypt(String envelope);
}
