package com.dongqh.luckyhub.auth.security;

import com.dongqh.luckyhub.auth.enums.AuthErrorCode;
import com.dongqh.luckyhub.auth.model.AuthenticatedUser;
import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expireSeconds;
    private final HmallJwtService hmallJwtService;

    @Autowired
    public JwtService(
            @Value("${luckyhub.security.jwt.secret}") String secret,
            @Value("${luckyhub.security.jwt.expiration}") long expireSeconds,
            HmallJwtService hmallJwtService
    ) {
        this.secretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );
        this.expireSeconds = expireSeconds;
        this.hmallJwtService = hmallJwtService;
    }

    /** Compatibility constructor retained for original LuckyHub unit tests. */
    public JwtService(String secret, long expireSeconds) {
        this(
                secret,
                expireSeconds,
                new HmallJwtService(new ClassPathResource("hmall-public.pem"))
        );
    }

    public String generate(
            AuthenticatedUser user,
            String sessionId
    ) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(expireSeconds);

        return Jwts.builder()
                .subject(user.userId().toString())
                .id(sessionId)
                .claim("username", user.username())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    public JwtPayload parse(String token) {
        LoginPrincipal principal = hmallJwtService.parse(token);
        return new JwtPayload(
                principal.userId(),
                principal.username(),
                principal.sessionId(),
                principal.role()
        );
    }

    public String newSessionId() {
        return UUID.randomUUID().toString();
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    private BusinessException invalidToken() {
        return new BusinessException(
                AuthErrorCode.INVALID_TOKEN
        );
    }
}
