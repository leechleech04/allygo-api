package com.allygo.allygo_api.auth.account.infrastructure.security;

import com.allygo.allygo_api.auth.account.application.port.AccountTokenPort;
import com.allygo.allygo_api.auth.account.domain.AccountAuthException;
import com.allygo.allygo_api.auth.account.domain.AccountAuthException.Reason;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import com.allygo.allygo_api.auth.phoneverification.infrastructure.security.JwtTokenProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtAccountTokenAdapter implements AccountTokenPort {
    private static final String VERIFICATION_PREFIX = "pvt_";
    private static final String REFRESH_PREFIX = "rt_";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProperties properties;
    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtAccountTokenAdapter(JwtTokenProperties properties) {
        this.properties = properties;
        try {
            this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.secretBase64()));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("auth.token.secret-base64 must contain a valid HMAC key", exception);
        }
    }

    @Override
    public VerificationTokenClaims parseVerificationToken(String token) {
        if (token == null || !token.startsWith(VERIFICATION_PREFIX)) {
            throw invalidVerificationToken();
        }
        try {
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token.substring(VERIFICATION_PREFIX.length()))
                    .getPayload();
            if (!"phone_verification".equals(claims.get("token_type", String.class))) {
                throw invalidVerificationToken();
            }
            long challengeId = Long.parseLong(claims.getSubject());
            if (challengeId <= 0 || claims.getIssuedAt() == null || claims.getExpiration() == null) {
                throw invalidVerificationToken();
            }
            VerificationPurpose purpose;
            try {
                purpose = VerificationPurpose.valueOf(claims.get("purpose", String.class));
            } catch (RuntimeException exception) {
                throw invalidVerificationToken();
            }
            return new VerificationTokenClaims(
                    challengeId,
                    purpose,
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant()
            );
        } catch (ExpiredJwtException exception) {
            throw AccountAuthException.of(Reason.VERIFICATION_TOKEN_EXPIRED, "휴대폰 인증 토큰이 만료되었습니다.");
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidVerificationToken();
        }
    }

    @Override
    public long requireAccessUserId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw AccountAuthException.unauthorized();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        if (token.isBlank()) {
            throw AccountAuthException.unauthorized();
        }
        try {
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!"access".equals(claims.get("token_type", String.class))) {
                throw AccountAuthException.unauthorized();
            }
            long userId = Long.parseLong(claims.getSubject());
            if (userId <= 0) {
                throw AccountAuthException.unauthorized();
            }
            return userId;
        } catch (JwtException | IllegalArgumentException exception) {
            throw AccountAuthException.unauthorized();
        }
    }

    @Override
    public String hashRefreshToken(String refreshToken) {
        return hash(refreshToken);
    }

    @Override
    public IssuedTokens issue(long userId, Instant issuedAt) {
        return issue(userId, UUID.randomUUID(), issuedAt, issuedAt.plus(properties.refreshTokenTtl()));
    }

    @Override
    public IssuedTokens rotate(
            long userId,
            UUID tokenFamilyId,
            Instant issuedAt,
            Instant refreshTokenExpiresAt
    ) {
        if (tokenFamilyId == null || refreshTokenExpiresAt == null || !issuedAt.isBefore(refreshTokenExpiresAt)) {
            throw new IllegalArgumentException("Refresh token family and future expiration are required");
        }
        return issue(userId, tokenFamilyId, issuedAt, refreshTokenExpiresAt);
    }

    private IssuedTokens issue(
            long userId,
            UUID tokenFamilyId,
            Instant issuedAt,
            Instant refreshTokenExpiresAt
    ) {
        String accessToken = Jwts.builder()
                .issuer(properties.issuer())
                .subject(Long.toString(userId))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(properties.accessTokenTtl())))
                .claim("token_type", "access")
                .signWith(key)
                .compact();
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String refreshToken = REFRESH_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        return new IssuedTokens(
                accessToken,
                refreshToken,
                hash(refreshToken),
                tokenFamilyId,
                properties.accessTokenTtl(),
                java.time.Duration.between(issuedAt, refreshTokenExpiresAt)
        );
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static AccountAuthException invalidVerificationToken() {
        return AccountAuthException.of(Reason.INVALID_VERIFICATION_TOKEN, "유효하지 않은 휴대폰 인증 토큰입니다.");
    }
}
