package com.allygo.allygo_api.auth.domain.verification;

import com.allygo.allygo_api.auth.infrastructure.config.VerificationProperties;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class PhoneVerificationPolicy {

    private final VerificationProperties properties;

    public PhoneVerificationPolicy(VerificationProperties properties) {
        this.properties = properties;
    }

    public OffsetDateTime codeExpiresAt(OffsetDateTime sentAt) {
        return sentAt.plus(properties.codeTtl());
    }

    public OffsetDateTime resendAvailableAt(OffsetDateTime sentAt) {
        return sentAt.plus(properties.resendCooldown());
    }

    public boolean canResend(OffsetDateTime sentAt, OffsetDateTime now) {
        return !now.isBefore(resendAvailableAt(sentAt));
    }

    public boolean exceedsDailySendLimit(long sendsToday) {
        return sendsToday >= properties.maxDailySends();
    }

    public int maxAttempts() {
        return properties.maxAttempts();
    }
}
