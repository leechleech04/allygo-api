package com.allygo.allygo_api.auth.account.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth.rate-limit.token-refresh")
public record TokenRefreshRateLimitProperties(Duration window, int maxAttempts, Duration blockDuration) {
    public TokenRefreshRateLimitProperties {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("auth.rate-limit.token-refresh.window must be positive");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("auth.rate-limit.token-refresh.max-attempts must be positive");
        }
        if (blockDuration == null || blockDuration.isZero() || blockDuration.isNegative()) {
            throw new IllegalArgumentException("auth.rate-limit.token-refresh.block-duration must be positive");
        }
    }
}
