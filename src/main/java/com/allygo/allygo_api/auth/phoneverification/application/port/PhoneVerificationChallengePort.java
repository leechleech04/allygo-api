package com.allygo.allygo_api.auth.phoneverification.application.port;

import com.allygo.allygo_api.auth.phoneverification.domain.PhoneVerificationChallenge;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;

import java.time.Instant;
import java.util.Optional;

public interface PhoneVerificationChallengePort {
    void lockPhoneAndPurpose(String phoneE164, VerificationPurpose purpose);
    Optional<PhoneVerificationChallenge> findByIdForUpdate(Long challengeId);
    Optional<PhoneVerificationChallenge> findLatestForUpdate(String phoneE164, VerificationPurpose purpose);
    int sumSendCount(String phoneE164, VerificationPurpose purpose, Instant fromInclusive, Instant toExclusive);
    PhoneVerificationChallenge create(String phoneE164, VerificationPurpose purpose, String codeHash, Instant expiresAt, Instant createdAt);
    PhoneVerificationChallenge resend(Long challengeId, String codeHash, Instant expiresAt);
    void expire(Long challengeId, Instant expiresAt);
    int registerMismatch(Long challengeId, Instant now, int maxAttempts);
    PhoneVerificationChallenge markVerified(Long challengeId, Instant verifiedAt);
}
