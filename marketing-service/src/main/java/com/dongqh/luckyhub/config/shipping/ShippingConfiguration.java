package com.dongqh.luckyhub.config.shipping;

import com.dongqh.luckyhub.domain.crypto.shipping.AddressCipher;
import com.dongqh.luckyhub.domain.crypto.shipping.AesGcmAddressCipher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ShippingProperties.class)
public class ShippingConfiguration {

    @Bean
    public AddressCipher addressCipher(ShippingProperties properties) {
        return new AesGcmAddressCipher(properties);
    }
}
