package com.allygo.allygo_api.auth.phoneverification.infrastructure.security;

import com.allygo.allygo_api.auth.phoneverification.application.port.VerificationTokenPort;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtVerificationTokenAdapter implements VerificationTokenPort {
    private static final String TOKEN_PREFIX = "pvt_";

    private final String issuer;
    private final SecretKey key;
    private final Duration ttl;

    public JwtVerificationTokenAdapter(JwtTokenProperties properties) {
        this.issuer = properties.issuer();
        this.ttl = properties.verificationTokenTtl();
        try {
            this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.secretBase64()));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("auth.token.secret-base64 must contain a valid HMAC key", exception);
        }
    }

    @Override
    public String issue(Long challengeId, VerificationPurpose purpose, Instant verifiedAt) {
        return TOKEN_PREFIX + Jwts.builder()
                .issuer(issuer)
                .subject(challengeId.toString())
                .id("phone-verification-" + challengeId)
                .issuedAt(Date.from(verifiedAt))
                .expiration(Date.from(verifiedAt.plus(ttl)))
                .claim("token_type", "phone_verification")
                .claim("purpose", purpose.name())
                .signWith(key)
                .compact();
    }

    @Override
    public Duration ttl() {
        return ttl;
    }
}
