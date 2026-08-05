package com.allygo.allygo_api.auth.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "auth.rate-limit")
public record RateLimitProperties(
        @Valid @NotNull Limit login,
        @Valid @NotNull Limit tokenRefresh
) {
    public record Limit(
            @NotNull Duration window,
            @Min(1) int maxAttempts,
            @NotNull Duration blockDuration
    ) {
        public Limit {
            requirePositive(window, "window");
            requirePositive(blockDuration, "blockDuration");
        }

        private static void requirePositive(Duration value, String name) {
            if (value != null && (value.isZero() || value.isNegative())) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        }
    }
}
