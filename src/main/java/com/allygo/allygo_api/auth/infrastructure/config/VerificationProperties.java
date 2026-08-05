package com.allygo.allygo_api.auth.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "auth.verification")
public record VerificationProperties(
        @NotNull Duration codeTtl,
        @NotNull Duration resendCooldown,
        @Min(1) int maxAttempts,
        @Min(1) int maxDailySends,
        @NotBlank String codePepperBase64
) {
    public VerificationProperties {
        requirePositive(codeTtl, "codeTtl");
        requirePositive(resendCooldown, "resendCooldown");
    }

    private static void requirePositive(Duration value, String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
