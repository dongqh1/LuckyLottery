package com.dongqh.hmgateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public PublicKey jwtVerificationKey(JwtProperties properties) {
        try (InputStream input = properties.getPublicKeyLocation().getInputStream()) {
            return CertificateFactory.getInstance("X.509")
                    .generateCertificate(input)
                    .getPublicKey();
        } catch (IOException | CertificateException e) {
            throw new IllegalStateException("Failed to load JWT verification certificate", e);
        }
    }
}
