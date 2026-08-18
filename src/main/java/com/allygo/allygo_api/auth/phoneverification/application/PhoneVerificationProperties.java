package com.allygo.allygo_api.auth.phoneverification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "auth.verification")
public record PhoneVerificationProperties(
        Duration codeTtl,
        Duration resendCooldown,
        int maxAttempts,
        int maxDailySends,
        ZoneId dailyLimitZone,
        String codePepperBase64
) {
    public PhoneVerificationProperties {
        if (codeTtl == null || codeTtl.isNegative() || codeTtl.isZero()) {
            throw new IllegalArgumentException("auth.verification.code-ttl must be positive");
        }
        if (resendCooldown == null || resendCooldown.isNegative()) {
            throw new IllegalArgumentException("auth.verification.resend-cooldown must not be negative");
        }
        if (maxAttempts <= 0 || maxDailySends <= 0) {
            throw new IllegalArgumentException("verification limits must be positive");
        }
        if (dailyLimitZone == null) {
            throw new IllegalArgumentException("auth.verification.daily-limit-zone is required");
        }
        if (codePepperBase64 == null || codePepperBase64.isBlank()) {
            throw new IllegalArgumentException("auth.verification.code-pepper-base64 is required");
        }
    }
}
