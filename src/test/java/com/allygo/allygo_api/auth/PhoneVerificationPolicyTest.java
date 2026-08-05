package com.allygo.allygo_api.auth;

import com.allygo.allygo_api.auth.domain.verification.PhoneVerificationPolicy;
import com.allygo.allygo_api.auth.infrastructure.config.VerificationProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneVerificationPolicyTest {

    private final PhoneVerificationPolicy policy = new PhoneVerificationPolicy(
            new VerificationProperties(
                    Duration.ofSeconds(180), Duration.ofSeconds(60), 5, 5,
                    "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
            )
    );

    @Test
    void appliesExpiryResendAttemptAndDailyLimitsFromConfiguration() {
        OffsetDateTime sentAt = OffsetDateTime.parse("2026-08-05T03:00:00Z");

        assertThat(policy.codeExpiresAt(sentAt)).isEqualTo(sentAt.plusSeconds(180));
        assertThat(policy.canResend(sentAt, sentAt.plusSeconds(59))).isFalse();
        assertThat(policy.canResend(sentAt, sentAt.plusSeconds(60))).isTrue();
        assertThat(policy.maxAttempts()).isEqualTo(5);
        assertThat(policy.exceedsDailySendLimit(4)).isFalse();
        assertThat(policy.exceedsDailySendLimit(5)).isTrue();
    }
}
