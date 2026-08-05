package com.allygo.allygo_api.auth;

import com.allygo.allygo_api.auth.domain.verification.VerificationPurpose;
import com.allygo.allygo_api.auth.infrastructure.persistence.verification.PhoneVerificationChallengeJpaEntity;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneVerificationChallengeTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-05T12:00:00Z");

    @Test
    void fifthMismatchExpiresChallengeImmediately() {
        PhoneVerificationChallengeJpaEntity challenge = challenge();

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(challenge.registerMismatch(NOW.plusSeconds(attempt), 5))
                    .isEqualTo(5 - attempt);
        }

        assertThat(challenge.getAttemptCount()).isEqualTo(5);
        assertThat(challenge.getExpiresAt()).isEqualTo(NOW.plusSeconds(5));
        assertThatThrownBy(() -> challenge.markVerified(NOW.plusSeconds(6), 5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resendInvalidatesOldCodeAndResetsAttempts() {
        PhoneVerificationChallengeJpaEntity challenge = challenge();
        challenge.registerMismatch(NOW.plusSeconds(1), 5);

        challenge.resend("new-hash", NOW.plusMinutes(4));

        assertThat(challenge.getCodeHash()).isEqualTo("new-hash");
        assertThat(challenge.getAttemptCount()).isZero();
        assertThat(challenge.getSendCount()).isEqualTo(2);
    }

    private PhoneVerificationChallengeJpaEntity challenge() {
        return PhoneVerificationChallengeJpaEntity.create(
                "+821012345678",
                VerificationPurpose.SIGN_UP,
                "old-hash",
                NOW,
                NOW.plusMinutes(3)
        );
    }
}
