package com.dongqh.fulfillmentservice.shipping.config;

import com.dongqh.fulfillmentservice.shipping.crypto.AddressCipher;
import com.dongqh.fulfillmentservice.shipping.crypto.AesGcmAddressCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AddressCipherConfiguration {

    @Bean
    AddressCipher addressCipher(
            @Value("${luckyhub.fulfillment.address-cipher.key-version:v1}") String keyVersion,
            @Value("${luckyhub.fulfillment.address-cipher.key-base64:}") String base64Key
    ) {
        return new AesGcmAddressCipher(keyVersion, base64Key);
    }
}
