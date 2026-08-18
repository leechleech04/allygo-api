package com.allygo.allygo_api.auth.phoneverification.infrastructure.security;

import com.allygo.allygo_api.auth.phoneverification.application.port.AccessTokenPort;
import com.allygo.allygo_api.auth.phoneverification.domain.PhoneVerificationException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;

@Component
public class JwtAccessTokenAdapter implements AccessTokenPort {
    private static final String BEARER_PREFIX = "Bearer ";
    private final String issuer;
    private final SecretKey key;

    public JwtAccessTokenAdapter(JwtTokenProperties properties) {
        this.issuer = properties.issuer();
        try {
            this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.secretBase64()));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("auth.token.secret-base64 must contain a valid HMAC key", exception);
        }
    }

    @Override
    public Long requireUserId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw PhoneVerificationException.unauthorized();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        if (token.isBlank()) {
            throw PhoneVerificationException.unauthorized();
        }
        try {
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!"access".equals(claims.get("token_type", String.class))) {
                throw PhoneVerificationException.unauthorized();
            }
            String subject = claims.getSubject();
            long userId = Long.parseLong(subject);
            if (userId <= 0) {
                throw PhoneVerificationException.unauthorized();
            }
            return userId;
        } catch (JwtException | IllegalArgumentException exception) {
            throw PhoneVerificationException.unauthorized();
        }
    }
}
