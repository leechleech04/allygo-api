package com.allygo.allygo_api.auth.phoneverification.domain;

import java.time.Instant;

public record PhoneVerificationChallenge(
        Long challengeId,
        String phoneE164,
        VerificationPurpose purpose,
        String codeHash,
        short attemptCount,
        short sendCount,
        Instant expiresAt,
        Instant verifiedAt,
        Instant consumedAt,
        Instant createdAt
) {
    public boolean canBeResent() {
        return verifiedAt == null && consumedAt == null;
    }
}
