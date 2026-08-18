package com.allygo.allygo_api.auth.phoneverification.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth.token")
public record JwtTokenProperties(String issuer, String secretBase64, Duration verificationTokenTtl) {
    public JwtTokenProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("auth.token.issuer is required");
        }
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalArgumentException("auth.token.secret-base64 is required");
        }
        if (verificationTokenTtl == null || verificationTokenTtl.isZero() || verificationTokenTtl.isNegative()) {
            throw new IllegalArgumentException("auth.token.verification-token-ttl must be positive");
        }
    }
}
