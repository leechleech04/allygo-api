package com.allygo.allygo_api.auth.account.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth.rate-limit.login")
public record AccountAuthProperties(Duration window, int maxAttempts, Duration blockDuration) {
    public AccountAuthProperties {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("auth.rate-limit.login.window must be positive");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("auth.rate-limit.login.max-attempts must be positive");
        }
        if (blockDuration == null || blockDuration.isZero() || blockDuration.isNegative()) {
            throw new IllegalArgumentException("auth.rate-limit.login.block-duration must be positive");
        }
    }
}
