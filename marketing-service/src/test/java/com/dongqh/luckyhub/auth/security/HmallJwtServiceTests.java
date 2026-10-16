package com.dongqh.luckyhub.security.auth;

import com.dongqh.luckyhub.domain.model.auth.LoginPrincipal;
import com.dongqh.luckyhub.common.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmallJwtServiceTests {

    private static KeyPair trustedKeyPair;
    private static HmallJwtService service;

    @BeforeAll
    static void setUpKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        trustedKeyPair = generator.generateKeyPair();
        service = new HmallJwtService(trustedKeyPair.getPublic());
    }

    @Test
    void parsesValidRs256TokenWithHmallUserAndRoleClaims() {
        String token = token(trustedKeyPair, 9001L, "ADMIN", Instant.now().plusSeconds(60));

        LoginPrincipal principal = service.parse(token);

        assertThat(principal.userId()).isEqualTo(9001L);
    }

    @Test
    void rejectsTokenSignedByUntrustedPrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair attackerKeyPair = generator.generateKeyPair();
        String token = token(attackerKeyPair, 9001L, "USER", Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> service.parse(token))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String token = token(trustedKeyPair, 9001L, "USER", Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> service.parse(token))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsRsaTokensUsingAnAlgorithmOtherThanRs256() {
        String token = Jwts.builder()
                .claim("user", 9001L)
                .claim("role", "USER")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(trustedKeyPair.getPrivate(), Jwts.SIG.RS512)
                .compact();

        assertThatThrownBy(() -> service.parse(token))
                .isInstanceOf(BusinessException.class);
    }

    private static String token(KeyPair keyPair, Long userId, String role, Instant expiration) {
        return Jwts.builder()
                .claim("user", userId)
                .claim("role", role)
                .expiration(Date.from(expiration))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}

