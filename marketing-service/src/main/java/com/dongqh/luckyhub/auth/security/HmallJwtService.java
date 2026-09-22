package com.dongqh.luckyhub.auth.security;

import com.dongqh.luckyhub.auth.enums.AuthErrorCode;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;
import java.util.Base64;

/** Verifies the RS256 access tokens issued by Black Horse Mall. */
@Service
public class HmallJwtService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";

    private final PublicKey publicKey;

    @Autowired
    public HmallJwtService(
            @Value("${luckyhub.security.hmall-jwt.public-key:classpath:hmall-public.pem}")
            Resource publicKeyResource
    ) {
        this(loadPublicKey(publicKeyResource));
    }

    public HmallJwtService(PublicKey publicKey) {
        if (publicKey == null || !"RSA".equalsIgnoreCase(publicKey.getAlgorithm())) {
            throw new IllegalArgumentException("Hmall JWT public key must be RSA");
        }
        this.publicKey = publicKey;
    }

    public LoginPrincipal parse(String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }

        try {
            Jws<Claims> signedClaims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);
            if (!"RS256".equals(signedClaims.getHeader().getAlgorithm())) {
                throw invalidToken();
            }
            Claims claims = signedClaims.getPayload();

            if (claims.getExpiration() == null) {
                throw invalidToken();
            }

            Long userId = parseUserId(claims.get("user"));
            String role = claims.get("role", String.class);
            if (!ROLE_ADMIN.equals(role) && !ROLE_USER.equals(role)) {
                throw invalidToken();
            }

            return new LoginPrincipal(
                    userId,
                    userId.toString(),
                    null,
                    role
            );
        } catch (JwtException | IllegalArgumentException | ArithmeticException exception) {
            throw invalidToken();
        }
    }

    private static Long parseUserId(Object claim) {
        if (claim == null) {
            throw invalidToken();
        }
        if (claim instanceof Number number) {
            return new BigDecimal(number.toString()).longValueExact();
        }
        return Long.valueOf(claim.toString());
    }

    private static PublicKey loadPublicKey(Resource resource) {
        try {
            byte[] bytes = resource.getInputStream().readAllBytes();
            String pem = new String(bytes, StandardCharsets.US_ASCII);
            if (pem.contains("-----BEGIN CERTIFICATE-----")) {
                return CertificateFactory.getInstance("X.509")
                        .generateCertificate(new java.io.ByteArrayInputStream(bytes))
                        .getPublicKey();
            }

            String encoded = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(encoded);
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to load Hmall JWT public key", exception);
        }
    }

    private static BusinessException invalidToken() {
        return new BusinessException(AuthErrorCode.INVALID_TOKEN);
    }
}